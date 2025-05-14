package com.example.weather;

import android.content.Context;
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
    private String city;
    private String forecastData;
    private String selectedDay;
    private HashMap<String, ArrayList<HourlyForecastItem>> forecastByDay;

    private final String API_KEY = "dad72bbf3ec207a8585ff3b7dcbcf9a8";
    private final String FORECAST_URL = "https://api.openweathermap.org/data/2.5/forecast";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hourly_forecast);

        lvHourlyForecast = findViewById(R.id.lvHourlyForecast);
        city = getIntent().getStringExtra("city");
        forecastData = getIntent().getStringExtra("forecast_data");

        selectedDay = getIntent().getStringExtra("selected_day");
        if (selectedDay == null) {
            selectedDay = getDayOfWeek(new Date());
        }

        if (city != null && forecastData != null) {
            new FetchHourlyForecastTask().execute();
        } else if (city != null) {
            new FetchHourlyForecastTask().execute(city);
        } else {
            Toast.makeText(this, "City not provided", Toast.LENGTH_SHORT).show();
            finish();
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
            // Log weather data for debugging
            Log.d("WeatherApp", "Weather - Time: " + description + ", Weather ID: " + weatherId + ", Icon Code: " + iconCode);

            // Determine if it's day or night based on the forecast time
            boolean isDayTime = isDayTime(forecastDate);
            Log.d("WeatherApp", "Forecast Time: " + forecastDate + ", Is Daytime: " + isDayTime);

            // Check for windy conditions first (e.g., wind speed > 10 m/s)
            if (windSpeed > 10) {
                resourceId = R.drawable.windy_icon;
            }
            // Thunderstorm
            else if (weatherId >= 200 && weatherId < 300) {
                resourceId = R.drawable.thunder_icon;
            }
            // Drizzle
            else if (weatherId >= 300 && weatherId < 400) {
                resourceId = R.drawable.rain_icon;
            }
            // Rain
            else if (weatherId >= 500 && weatherId < 600) {
                if (weatherId == 500 || weatherId == 501 || description.toLowerCase().contains("light rain")) {
                    resourceId = R.drawable.light_rain_icon;
                } else {
                    resourceId = R.drawable.heavy_rain_icon;
                }
            }
            // Snow
            else if (weatherId >= 600 && weatherId < 700) {
                resourceId = R.drawable.snow_icon;
            }
            // Atmosphere (mist, fog, haze, etc.)
            else if (weatherId >= 700 && weatherId < 800) {
                if (weatherId == 741 || description.toLowerCase().contains("fog")) {
                    resourceId = R.drawable.fog_icon;
                } else {
                    resourceId = R.drawable.mist_icon;
                }
            }
            // Clear
            else if (weatherId == 800) {
                if (isDayTime) {
                    resourceId = R.drawable.sun_icon;
                } else {
                    resourceId = R.drawable.moon_icon;
                }
            }
            // Clouds
            else if (weatherId > 800) {
                if (weatherId == 801) { // Few clouds
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
            }
            // Default
            else {
                resourceId = R.drawable.sun_icon;
            }
            imageView.setImageResource(resourceId);
        } catch (Exception e) {
            Log.w("WeatherApp", "Icon resource not found for " + description + ": " + e.getMessage());
            imageView.setImageResource(R.drawable.cloud_icon); // Fallback to cloud icon
        }
    }

    private boolean isDayTime(Date forecastDate) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(forecastDate);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        // Approximate sunrise at 6:00 AM and sunset at 6:00 PM
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
            new FetchHourlyForecastTask().execute();
        }
    }
}