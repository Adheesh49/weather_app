package com.example.weather;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;



import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;


public class HourlyForecastActivity extends AppCompatActivity {

    private ListView lvHourlyForecast;
    private TextView tvSevereWeatherAlerts;
    private TextView tvHourlyHeaderCustom;
    private String city;
    private String forecastData;
    private String selectedDay;
    private HashMap<String, ArrayList<HourlyForecastItem>> forecastByDay;
    private static final String CHANNEL_ID = "SevereWeatherChannel";
    private static final int NOTIFICATION_ID = 1;

    private final String API_KEY = "dad72bbf3ec207a8585ff3b7dcbcf9a8";
    private final String FORECAST_URL = "https://api.openweathermap.org/data/2.5/forecast";
    private final String GEOCODING_URL = "http://api.openweathermap.org/geo/1.0/direct";
    private final String ONECALL_URL = "https://api.openweathermap.org/data/2.5/onecall";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsControllerCompat windowInsetsController =
                    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (windowInsetsController != null) {
                windowInsetsController.hide(WindowInsetsCompat.Type.statusBars());
                windowInsetsController.setSystemBarsBehavior(
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            // For older APIs (before Android 11)
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            );
        }


        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_hourly_forecast);

        tvHourlyHeaderCustom = findViewById(R.id.tvHourlyHeaderCustom);

        lvHourlyForecast = findViewById(R.id.lvHourlyForecast);
        tvSevereWeatherAlerts = findViewById(R.id.tvSevereWeatherAlerts);
        city = getIntent().getStringExtra("city");
        forecastData = getIntent().getStringExtra("forecast_data");
        selectedDay = getIntent().getStringExtra("selected_day");

        if (selectedDay == null) {
            selectedDay = getDayOfWeek(new Date());
        }

        createNotificationChannel();

        if (city != null && forecastData != null) {
            new FetchCoordinatesTask().execute(city);
            processPassedForecastData(forecastData);
        } else if (city != null) {
            new FetchCoordinatesTask().execute(city);
        } else {
            if (tvSevereWeatherAlerts != null) tvSevereWeatherAlerts.setText("City not provided. Cannot fetch data.");
            finish();
        }
    }

    private void processPassedForecastData(String data) {
        if (data != null) {
            try {
                HashMap<String, ArrayList<HourlyForecastItem>> parsedData = parseForecastData(data);
                if (parsedData != null) {
                    forecastByDay = parsedData;
                    updateForecastList(forecastByDay.getOrDefault(selectedDay, new ArrayList<>()));
                } else {
                    if (tvSevereWeatherAlerts != null) tvSevereWeatherAlerts.setText("Error parsing provided forecast data");
                }
            } catch (Exception e) {
                Log.e("WeatherApp", "Error processing passed forecast data: " + e.getMessage());
                if (tvSevereWeatherAlerts != null) tvSevereWeatherAlerts.setText("Error processing forecast");
            }
        }
    }

    private HashMap<String, ArrayList<HourlyForecastItem>> parseForecastData(String jsonData) {

        final String LOG_TAG = "WeatherApp"; // Or use your class TAG

        if (jsonData == null || jsonData.isEmpty()) {
            Log.e(LOG_TAG, "jsonData is null or empty in parseForecastData.");
            return null;
        }
        try {
            HashMap<String, ArrayList<HourlyForecastItem>> forecastMap = new HashMap<>();
            JSONObject jsonObject = new JSONObject(jsonData);
            JSONArray listArray = jsonObject.getJSONArray("list");


            SimpleDateFormat sdfInputUtc = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
            sdfInputUtc.setTimeZone(TimeZone.getTimeZone("UTC")); // Tell SimpleDateFormat the input string is UTC

            SimpleDateFormat sdfDayLocal = new SimpleDateFormat("EEEE", Locale.getDefault());

            SimpleDateFormat sdfTimeLocal = new SimpleDateFormat("HH:mm", Locale.getDefault()); // For 24-hour format like "15:00"

            for (int i = 0; i < listArray.length(); i++) {
                JSONObject forecastObj = listArray.getJSONObject(i);
                String dtTxt = forecastObj.getString("dt_txt"); // This is a UTC string, e.g., "2025-05-28 12:00:00"
                Date utcDate = sdfInputUtc.parse(dtTxt); // 'utcDate' now correctly represents the UTC moment in time
                String localDayName = sdfDayLocal.format(utcDate);
                String localTimeForDisplay = sdfTimeLocal.format(utcDate);
                double temp = forecastObj.getJSONObject("main").getDouble("temp");
                String desc = forecastObj.getJSONArray("weather").getJSONObject(0).getString("description");
                int weatherId = forecastObj.getJSONArray("weather").getJSONObject(0).getInt("id");
                String iconCode = forecastObj.getJSONArray("weather").getJSONObject(0).getString("icon");
                double windSpeed = forecastObj.getJSONObject("wind").getDouble("speed");


                HourlyForecastItem item = new HourlyForecastItem(localTimeForDisplay, localDayName, temp, desc, weatherId, iconCode, windSpeed, utcDate);
                forecastMap.computeIfAbsent(localDayName, k -> new ArrayList<>()).add(item);
            }
            return forecastMap;
        } catch (JSONException | ParseException e) {
            Log.e(LOG_TAG, "JSON or date parsing error in parseForecastData: " + e.getMessage(), e);
            return null;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Severe Weather Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications for severe weather alerts");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void sendSevereWeatherNotification(String alertText) {
        Intent intent = new Intent(this, HourlyForecastActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Severe Weather Alert")
                .setContentText(alertText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }

    private class FetchCoordinatesTask extends AsyncTask<String, Void, double[]> {
        @Override
        protected double[] doInBackground(String... params) {
            String cityToGeocode = params[0];
            String urlString = GEOCODING_URL + "?q=" + cityToGeocode + "&limit=1&appid=" + API_KEY;
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.connect();
                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                JSONArray jsonArray = new JSONArray(response.toString());
                if (jsonArray.length() > 0) {
                    JSONObject cityData = jsonArray.getJSONObject(0);
                    return new double[]{cityData.getDouble("lat"), cityData.getDouble("lon")};
                }
            } catch (IOException | JSONException e) {
                Log.e("WeatherApp", "Geocoding fetch error: " + e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(double[] coordinates) {
            if (coordinates != null) {
                new FetchSevereWeatherAlertsTask().execute(coordinates[0], coordinates[1]);
                if (forecastData == null) new FetchHourlyForecastTask().execute(city);
            } else {
                if (tvSevereWeatherAlerts != null) tvSevereWeatherAlerts.setText("Unable to fetch severe weather alerts");
                if (forecastData == null) new FetchHourlyForecastTask().execute(city);
            }
        }
    }

    private class FetchSevereWeatherAlertsTask extends AsyncTask<Double, Void, String> {
        @Override
        protected String doInBackground(Double... params) {
            String urlString = ONECALL_URL + "?lat=" + params[0] + "&lon=" + params[1] + "&exclude=hourly,daily,minutely,current&appid=" + API_KEY;
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.connect();
                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            } catch (IOException e) {
                Log.e("WeatherApp", "Severe weather alerts fetch error: " + e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            if (tvSevereWeatherAlerts == null) return;
            if (result != null) {
                try {
                    JSONObject jsonObject = new JSONObject(result);
                    if (jsonObject.has("alerts")) {
                        JSONArray alertsArray = jsonObject.getJSONArray("alerts");
                        if (alertsArray.length() > 0) {
                            StringBuilder alertsText = new StringBuilder();
                            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
                            for (int i = 0; i < alertsArray.length(); i++) {
                                JSONObject alert = alertsArray.getJSONObject(i);
                                String event = alert.getString("event");
                                long start = alert.getLong("start") * 1000;
                                long end = alert.getLong("end") * 1000;
                                String description = alert.optString("description", "No description available.");
                                String sender = alert.optString("sender_name", "Unknown sender");
                                String alertSummary = event + " (from " + sdf.format(new Date(start)) + " to " + sdf.format(new Date(end)) + ")";
                                alertsText.append(alertSummary).append("\n").append(description).append("\nIssued by: ").append(sender).append("\n\n");
                                sendSevereWeatherNotification(alertSummary + ". Check app for details.");
                            }
                            tvSevereWeatherAlerts.setText(alertsText.toString().trim());
                            tvSevereWeatherAlerts.setVisibility(View.VISIBLE);
                        } else {
                            tvSevereWeatherAlerts.setText("No severe weather alerts");
                            tvSevereWeatherAlerts.setVisibility(View.VISIBLE);
                        }
                    } else {
                        tvSevereWeatherAlerts.setText("No severe weather alerts");
                        tvSevereWeatherAlerts.setVisibility(View.VISIBLE);
                    }
                } catch (JSONException e) {
                    Log.e("WeatherApp", "Severe weather alerts parsing error: " + e.getMessage());
                    tvSevereWeatherAlerts.setText("Error parsing severe weather alerts");
                    tvSevereWeatherAlerts.setVisibility(View.VISIBLE);
                }
            } else {
                tvSevereWeatherAlerts.setText("No severe weather alerts");
                tvSevereWeatherAlerts.setVisibility(View.VISIBLE);
            }
        }
    }

    private class FetchHourlyForecastTask extends AsyncTask<String, Void, HashMap<String, ArrayList<HourlyForecastItem>>> {
        @Override
        protected HashMap<String, ArrayList<HourlyForecastItem>> doInBackground(String... params) {
            String cityForFetch = params.length > 0 ? params[0] : null;
            String resultJson = null;
            if (cityForFetch != null) {
                String urlString = FORECAST_URL + "?q=" + cityForFetch + "&units=metric&cnt=40&appid=" + API_KEY;
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    connection.connect();
                    InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    resultJson = response.toString();
                } catch (IOException e) {
                    Log.e("WeatherApp", "Hourly forecast fetch error: " + e.getMessage());
                    return null;
                }
            }
            return (resultJson != null) ? parseForecastData(resultJson) : null;
        }

        @Override
        protected void onPostExecute(HashMap<String, ArrayList<HourlyForecastItem>> result) {
            if (result != null) {
                forecastByDay = result;
                updateForecastList(forecastByDay.getOrDefault(selectedDay, new ArrayList<>()));
            } else {
                if (tvSevereWeatherAlerts != null) tvSevereWeatherAlerts.setText("Error fetching hourly forecast");
            }
        }
    }

    private void updateForecastList(ArrayList<HourlyForecastItem> items) {
        if (lvHourlyForecast == null) return;
        if (items == null || items.isEmpty()) {
            lvHourlyForecast.setAdapter(null);
        } else {
            HourlyForecastAdapter adapter = new HourlyForecastAdapter(HourlyForecastActivity.this, items);
            lvHourlyForecast.setAdapter(adapter);
        }
    }

    private static class HourlyForecastItem {
        String time, day, description, iconCode;
        double temperature, windSpeed;
        int weatherId;
        Date date;
        HourlyForecastItem(String time, String day, double temp, String desc, int id, String icon, double wind, Date dt) {
            this.time = time;
            this.day = day;
            this.temperature = temp;
            this.description = desc;
            this.weatherId = id;
            this.iconCode = icon;
            this.windSpeed = wind;
            this.date = dt;
        }
    }

    private class HourlyForecastAdapter extends ArrayAdapter<HourlyForecastItem> {
        HourlyForecastAdapter(Context context, ArrayList<HourlyForecastItem> items) {
            super(context, 0, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_item_hourly_forecast, parent, false);
            }
            HourlyForecastItem item = getItem(position);
            TextView tvHourlyForecastTextInItem = convertView.findViewById(R.id.tvHourlyForecast);
            ImageView ivHourlyIcon = convertView.findViewById(R.id.ivHourlyIcon);
            if (item != null) {
                tvHourlyForecastTextInItem.setText(String.format(Locale.getDefault(), "%s - %s: %.1f°C, %s", item.time, item.day, item.temperature, capitalizeFirstLetter(item.description)));
                tvHourlyForecastTextInItem.setTextColor(getResources().getColor(R.color.text_primary_dark));
                setHourlyIcon(ivHourlyIcon, item.weatherId, item.iconCode, item.description, item.windSpeed, item.date);
            }
            return convertView;
        }
    }

    private void setHourlyIcon(ImageView imageView, int weatherId, String iconCode, String description, double windSpeed, Date forecastDate) {
        if (imageView == null || description == null || forecastDate == null) return;
        int resourceId;
        try {
            boolean isDayTime = isDayTime(forecastDate);
            if (windSpeed > 10) resourceId = R.drawable.windy_icon;
            else if (weatherId >= 200 && weatherId < 300) resourceId = R.drawable.thunder_icon;
            else if (weatherId >= 300 && weatherId < 400) resourceId = R.drawable.rain_icon;
            else if (weatherId >= 500 && weatherId < 600) resourceId = (weatherId <= 501 || description.toLowerCase(Locale.ROOT).contains("light rain")) ? R.drawable.light_rain_icon : R.drawable.heavy_rain_icon;
            else if (weatherId >= 600 && weatherId < 700) resourceId = R.drawable.snow_icon;
            else if (weatherId >= 700 && weatherId < 800) resourceId = (weatherId == 741 || description.toLowerCase(Locale.ROOT).contains("fog")) ? R.drawable.fog_icon : R.drawable.mist_icon;
            else if (weatherId == 800) resourceId = isDayTime ? R.drawable.sun_icon : R.drawable.moon_icon;
            else if (weatherId > 800 && weatherId < 900) {
                if (weatherId == 801) resourceId = R.drawable.partly_cloudy_icon;
                else if (weatherId <= 804) resourceId = description.toLowerCase(Locale.ROOT).contains("broken clouds") ? R.drawable.broken_clouds_icon : R.drawable.cloud_icon;
                else resourceId = R.drawable.cloud_icon;
            } else resourceId = isDayTime ? R.drawable.sun_icon : R.drawable.cloud_icon;
            imageView.setImageResource(resourceId);
        } catch (Exception e) {
            Log.w("WeatherApp", "Icon resource error for " + description + ": " + e.getMessage());
            imageView.setImageResource(R.drawable.cloud_icon);
        }
    }

    private boolean isDayTime(Date forecastDate) {
        if (forecastDate == null) return true;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(forecastDate);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        return hour >= 6 && hour < 18;
    }

    private String capitalizeFirstLetter(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    private String getDayOfWeek(Date date) {
        if (date == null) return "Unknown Day";
        return new SimpleDateFormat("EEEE", Locale.getDefault()).format(date);
    }

    public void setSelectedDay(String day) {
        selectedDay = day;
        if (forecastByDay != null) {
            updateForecastList(forecastByDay.getOrDefault(selectedDay, new ArrayList<>()));
        } else {
            if (city != null) {
                new FetchCoordinatesTask().execute(city);
            } else if (tvSevereWeatherAlerts != null) {
                tvSevereWeatherAlerts.setText("City not set, cannot refresh forecast.");
            }
        }
    }
}