package com.example.weather;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

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

public class HourlyForecastActivity extends AppCompatActivity {

    private ListView lvHourlyForecast;
    private TextView tvSevereWeatherAlerts;
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
        setContentView(R.layout.activity_hourly_forecast);

        lvHourlyForecast = findViewById(R.id.lvHourlyForecast);
        tvSevereWeatherAlerts = findViewById(R.id.tvSevereWeatherAlerts);
        city = getIntent().getStringExtra("city");
        forecastData = getIntent().getStringExtra("forecast_data");

        selectedDay = getIntent().getStringExtra("selected_day");
        if (selectedDay == null) {
            selectedDay = getDayOfWeek(new Date());
        }

        // Create notification channel (required for Android 8.0+)
        createNotificationChannel();

        if (city != null && forecastData != null) {
            new FetchHourlyForecastTask().execute();
        } else if (city != null) {
            new FetchCoordinatesTask().execute(city);
        } else {
            Toast.makeText(this, "City not provided", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Severe Weather Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications for severe weather alerts");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
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
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private class FetchCoordinatesTask extends AsyncTask<String, Void, double[]> {
        @Override
        protected double[] doInBackground(String... params) {
            String city = params[0];
            String urlString = GEOCODING_URL + "?q=" + city + "&limit=1&appid=" + API_KEY;

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
                String result = response.toString();
                Log.d("WeatherApp", "Geocoding API Response: " + result);

                JSONArray jsonArray = new JSONArray(result);
                if (jsonArray.length() > 0) {
                    JSONObject cityData = jsonArray.getJSONObject(0);
                    double lat = cityData.getDouble("lat");
                    double lon = cityData.getDouble("lon");
                    return new double[]{lat, lon};
                }
            } catch (IOException | JSONException e) {
                Log.e("WeatherApp", "Geocoding fetch error: " + e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(double[] coordinates) {
            if (coordinates != null) {
                double lat = coordinates[0];
                double lon = coordinates[1];
                new FetchSevereWeatherAlertsTask().execute(lat, lon);
                new FetchHourlyForecastTask().execute(city);
            } else {
                Toast.makeText(HourlyForecastActivity.this, "Error fetching city coordinates", Toast.LENGTH_SHORT).show();
                tvSevereWeatherAlerts.setText("Unable to fetch severe weather alerts");
                new FetchHourlyForecastTask().execute(city);
            }
        }
    }

    private class FetchSevereWeatherAlertsTask extends AsyncTask<Double, Void, String> {
        @Override
        protected String doInBackground(Double... params) {
            double lat = params[0];
            double lon = params[1];
            String urlString = ONECALL_URL + "?lat=" + lat + "&lon=" + lon + "&exclude=hourly,daily,minutely,current&appid=" + API_KEY;

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
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                try {
                    JSONObject jsonObject = new JSONObject(result);
                    if (jsonObject.has("alerts")) {
                        JSONArray alertsArray = jsonObject.getJSONArray("alerts");
                        StringBuilder alertsText = new StringBuilder();
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

                        for (int i = 0; i < alertsArray.length(); i++) {
                            JSONObject alert = alertsArray.getJSONObject(i);
                            String event = alert.getString("event");
                            long start = alert.getLong("start") * 1000;
                            long end = alert.getLong("end") * 1000;
                            String description = alert.getString("description");
                            String sender = alert.getString("sender_name");

                            String alertSummary = event + " (from " + sdf.format(new Date(start)) + " to " + sdf.format(new Date(end)) + ")";
                            alertsText.append(alertSummary).append("\n").append(description).append("\nIssued by: ").append(sender).append("\n\n");

                            // Send notification for each alert
                            sendSevereWeatherNotification(alertSummary + ". Check app for details.");
                        }
                        tvSevereWeatherAlerts.setText(alertsText.toString().trim());
                    } else {
                        tvSevereWeatherAlerts.setText("No severe weather alerts");
                    }
                } catch (JSONException e) {
                    Log.e("WeatherApp", "Severe weather alerts parsing error: " + e.getMessage());
                    tvSevereWeatherAlerts.setText("Error parsing severe weather alerts");
                }
            } else {
                tvSevereWeatherAlerts.setText("Error fetching severe weather alerts");
            }
        }
    }

    private class FetchHourlyForecastTask extends AsyncTask<String, Void, HashMap<String, ArrayList<HourlyForecastItem>>> {
        @Override
        protected HashMap<String, ArrayList<HourlyForecastItem>> doInBackground(String... params) {
            String city = params.length > 0 ? params[0] : null;
            String urlString = city != null ? FORECAST_URL + "?q=" + city + "&units=metric&cnt=40&appid=" + API_KEY : null;
            String result = forecastData;

            if (result == null && city != null) {
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
                    result = response.toString();
                    Log.d("WeatherApp", "API Response: " + result);
                } catch (IOException e) {
                    Log.e("WeatherApp", "Hourly forecast fetch error: " + e.getMessage());
                    return null;
                }
            }

            if (result != null) {
                try {
                    HashMap<String, ArrayList<HourlyForecastItem>> forecastByDay = new HashMap<>();
                    JSONObject jsonObject = new JSONObject(result);
                    JSONArray listArray = jsonObject.getJSONArray("list");

                    SimpleDateFormat sdfInput = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    SimpleDateFormat sdfDay = new SimpleDateFormat("EEEE", Locale.getDefault());
                    SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm", Locale.getDefault());

                    for (int i = 0; i < listArray.length(); i++) {
                        JSONObject forecastObj = listArray.getJSONObject(i);
                        String dtTxt = forecastObj.getString("dt_txt");
                        Date date = sdfInput.parse(dtTxt);
                        String day = sdfDay.format(date);
                        double temp = forecastObj.getJSONObject("main").getDouble("temp");
                        String desc = forecastObj.getJSONArray("weather").getJSONObject(0).getString("description");
                        int weatherId = forecastObj.getJSONArray("weather").getJSONObject(0).getInt("id");
                        String iconCode = forecastObj.getJSONArray("weather").getJSONObject(0).getString("icon");
                        double windSpeed = forecastObj.getJSONObject("wind").getDouble("speed");
                        String time = sdfTime.format(date);

                        HourlyForecastItem item = new HourlyForecastItem(time, day, temp, desc, weatherId, iconCode, windSpeed, date);
                        forecastByDay.computeIfAbsent(day, k -> new ArrayList<>()).add(item);
                    }

                    return forecastByDay;
                } catch (JSONException | ParseException e) {
                    Log.e("WeatherApp", "JSON or date parsing error: " + e.getMessage());
                    return null;
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(HashMap<String, ArrayList<HourlyForecastItem>> result) {
            if (result != null) {
                forecastByDay = result;
                updateForecastList(forecastByDay.getOrDefault(selectedDay, new ArrayList<>()));
            } else {
                Toast.makeText(HourlyForecastActivity.this, "Error fetching hourly forecast", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateForecastList(ArrayList<HourlyForecastItem> items) {
        if (items.isEmpty()) {
            Toast.makeText(this, "No forecast data available for " + selectedDay, Toast.LENGTH_SHORT).show();
        }
        HourlyForecastAdapter adapter = new HourlyForecastAdapter(HourlyForecastActivity.this, items);
        lvHourlyForecast.setAdapter(adapter);
    }

    private class HourlyForecastItem {
        String time;
        String day;
        double temperature;
        String description;
        int weatherId;
        String iconCode;
        double windSpeed;
        Date date;

        HourlyForecastItem(String time, String day, double temperature, String description, int weatherId, String iconCode, double windSpeed, Date date) {
            this.time = time;
            this.day = day;
            this.temperature = temperature;
            this.description = description;
            this.weatherId = weatherId;
            this.iconCode = iconCode;
            this.windSpeed = windSpeed;
            this.date = date;
        }
    }

    private class HourlyForecastAdapter extends ArrayAdapter<HourlyForecastItem> {
        private final ArrayList<HourlyForecastItem> items;

        HourlyForecastAdapter(Context context, ArrayList<HourlyForecastItem> items) {
            super(context, 0, items);
            this.items = items;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_item_hourly_forecast, parent, false);
            }

            HourlyForecastItem item = items.get(position);
            TextView tvHourlyForecast = convertView.findViewById(R.id.tvHourlyForecast);
            ImageView ivHourlyIcon = convertView.findViewById(R.id.ivHourlyIcon);

            tvHourlyForecast.setText(String.format("%s - %s: %.1f°C, %s", item.time, item.day, item.temperature, capitalizeFirstLetter(item.description)));
            tvHourlyForecast.setTextColor(android.graphics.Color.parseColor("#212121"));
            tvHourlyForecast.setPadding(16, 12, 16, 12);

            setHourlyIcon(ivHourlyIcon, item.weatherId, item.iconCode, item.description, item.windSpeed, item.date);

            return convertView;
        }
    }

    private void setHourlyIcon(ImageView imageView, int weatherId, String iconCode, String description, double windSpeed, Date forecastDate) {
        int resourceId;

        try {
            Log.d("WeatherApp", "Weather - Time: " + description + ", Weather ID: " + weatherId + ", Icon Code: " + iconCode);

            boolean isDayTime = isDayTime(forecastDate);
            Log.d("WeatherApp", "Forecast Time: " + forecastDate + ", Is Daytime: " + isDayTime);

            if (windSpeed > 10) {
                resourceId = R.drawable.windy_icon;
            } else if (weatherId >= 200 && weatherId < 300) {
                resourceId = R.drawable.thunder_icon;
            } else if (weatherId >= 300 && weatherId < 400) {
                resourceId = R.drawable.rain_icon;
            } else if (weatherId >= 500 && weatherId < 600) {
                if (weatherId == 500 || weatherId == 501 || description.toLowerCase().contains("light rain")) {
                    resourceId = R.drawable.light_rain_icon;
                } else {
                    resourceId = R.drawable.heavy_rain_icon;
                }
            } else if (weatherId >= 600 && weatherId < 700) {
                resourceId = R.drawable.snow_icon;
            } else if (weatherId >= 700 && weatherId < 800) {
                if (weatherId == 741 || description.toLowerCase().contains("fog")) {
                    resourceId = R.drawable.fog_icon;
                } else {
                    resourceId = R.drawable.mist_icon;
                }
            } else if (weatherId == 800) {
                if (isDayTime) {
                    resourceId = R.drawable.sun_icon;
                } else {
                    resourceId = R.drawable.moon_icon;
                }
            } else if (weatherId > 800) {
                if (weatherId == 801) {
                    resourceId = R.drawable.partly_cloudy_icon;
                } else if (weatherId == 802 || weatherId == 803 || weatherId == 804) {
                    if (description.toLowerCase().contains("broken clouds")) {
                        resourceId = R.drawable.broken_clouds_icon;
                    } else {
                        resourceId = R.drawable.cloud_icon;
                    }
                } else {
                    resourceId = R.drawable.cloud_icon;
                }
            } else {
                resourceId = R.drawable.sun_icon;
            }
            imageView.setImageResource(resourceId);
        } catch (Exception e) {
            Log.w("WeatherApp", "Icon resource not found for " + description + ": " + e.getMessage());
            imageView.setImageResource(R.drawable.cloud_icon);
        }
    }

    private boolean isDayTime(Date forecastDate) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(forecastDate);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        return hour >= 6 && hour < 18;
    }

    private String capitalizeFirstLetter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    private String getDayOfWeek(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE", Locale.getDefault());
        return sdf.format(date);
    }

    public void setSelectedDay(String day) {
        selectedDay = day;
        if (forecastByDay != null) {
            updateForecastList(forecastByDay.getOrDefault(selectedDay, new ArrayList<>()));
        } else {
            new FetchCoordinatesTask().execute(city);
        }
    }
}