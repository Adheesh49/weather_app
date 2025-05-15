package com.example.weather;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.animation.ValueAnimator;

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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // UI Components
    private EditText etCityName;
    private Button btnSearch;
    private TextView tvCityName, tvTemperature, tvDescription, tvHumidity;
    private TextView tvWind, tvDate, tvForecast1, tvForecast2, tvForecast3;
    private TextView tvAlert;
    private ImageView ivWeatherIcon, ivWeatherMap, ivForecastIcon1, ivForecastIcon2, ivForecastIcon3;
    private ProgressBar progressBar;
    private LinearLayout alertSection;
    private View backgroundOld, backgroundNew;

    // OpenWeatherMap API key
    private final String API_KEY = "dad72bbf3ec207a8585ff3b7dcbcf9a8";
    // URLs for APIs
    private final String WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather";
    private final String FORECAST_URL = "https://api.openweathermap.org/data/2.5/forecast";
    private final String ONECALL_URL = "https://api.openweathermap.org/data/2.5/onecall";
    private final String MAP_URL = "https://tile.openweathermap.org/map/precipitation_new/3/%d/%d.png?appid=%s";

    // SharedPreferences for caching
    private SharedPreferences preferences;
    private static final String PREFS_NAME = "WeatherPrefs";
    private static final String LAST_CITY_KEY = "LastCity";

    // Store forecast data to pass to HourlyForecastActivity
    private String forecastData;

    // Callback interface
    public interface Callback {
        void onCoordinatesFetched(double lat, double lon);
        void onWeatherFetched();
        void onSevereWeatherFetched(String alertsText);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        etCityName = findViewById(R.id.etCityName);
        btnSearch = findViewById(R.id.btnSearch);
        tvCityName = findViewById(R.id.tvCityName);
        tvTemperature = findViewById(R.id.tvTemperature);
        tvDescription = findViewById(R.id.tvDescription);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvWind = findViewById(R.id.tvWind);
        tvDate = findViewById(R.id.tvDate);
        tvForecast1 = findViewById(R.id.tvForecast1);
        tvForecast2 = findViewById(R.id.tvForecast2);
        tvForecast3 = findViewById(R.id.tvForecast3);
        tvAlert = findViewById(R.id.tvAlert);
        ivWeatherIcon = findViewById(R.id.ivWeatherIcon);
        ivWeatherMap = findViewById(R.id.ivWeatherMap);
        ivForecastIcon1 = findViewById(R.id.ivForecastIcon1);
        ivForecastIcon2 = findViewById(R.id.ivForecastIcon2);
        ivForecastIcon3 = findViewById(R.id.ivForecastIcon3);
        progressBar = findViewById(R.id.progressBar);
        alertSection = findViewById(R.id.alertSection);
        backgroundOld = findViewById(R.id.backgroundOld);
        backgroundNew = findViewById(R.id.backgroundNew);

        // Initialize SharedPreferences
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Set current date
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        tvDate.setText(currentDate);

        // Load last searched city
        String lastCity = preferences.getString(LAST_CITY_KEY, "");
        if (!lastCity.isEmpty()) {
            etCityName.setText(lastCity);
            fetchWeatherData(lastCity);
        }

        // Search button click listener
        btnSearch.setOnClickListener(v -> {
            String city = etCityName.getText().toString().trim();
            if (!city.isEmpty()) {
                if (city.matches("[a-zA-Z\\s,]+")) {
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString(LAST_CITY_KEY, city);
                    editor.apply();

                    progressBar.setVisibility(View.VISIBLE);
                    fetchWeatherData(city);
                } else {
                    Toast.makeText(MainActivity.this, "Invalid city name", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "Please enter a city name", Toast.LENGTH_SHORT).show();
            }
        });

        // Make weather icon clickable with error handling and day selection
        ivWeatherIcon.setOnClickListener(v -> {
            String city = etCityName.getText().toString().trim();
            if (city.isEmpty() && tvCityName.getText().toString().contains(",")) {
                city = tvCityName.getText().toString().split(",")[0].trim();
            }
            if (!city.isEmpty()) {
                if (forecastData != null) {
                    try {
                        Intent intent = new Intent(MainActivity.this, HourlyForecastActivity.class);
                        intent.putExtra("city", city);
                        intent.putExtra("forecast_data", forecastData);
                        intent.putExtra("selected_day", getDayOfWeek(new Date())); // Default to today
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e("WeatherApp", "Error launching HourlyForecastActivity: " + e.getMessage());
                        Toast.makeText(MainActivity.this, "Failed to open hourly forecast", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Forecast data not available. Please search again.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "Please enter a city name first", Toast.LENGTH_SHORT).show();
            }
        });

        // Add click listeners for forecast icons to select day
        ivForecastIcon1.setOnClickListener(v -> openHourlyForecastWithDay(getDayFromForecast(tvForecast1)));
        ivForecastIcon2.setOnClickListener(v -> openHourlyForecastWithDay(getDayFromForecast(tvForecast2)));
        ivForecastIcon3.setOnClickListener(v -> openHourlyForecastWithDay(getDayFromForecast(tvForecast3)));
    }

    private void fetchWeatherData(String city) {
        new FetchWeatherTask(new Callback() {
            @Override
            public void onCoordinatesFetched(double lat, double lon) {
                new FetchWeatherMapTask(lat, lon).execute();
                new FetchSevereWeatherAlertsTask(this).execute(lat, lon);
            }

            @Override
            public void onWeatherFetched() {
                new FetchForecastTask().execute(city);
            }

            @Override
            public void onSevereWeatherFetched(String alertsText) {
                if (alertsText != null && !alertsText.equals("No severe weather alerts")) {
                    alertSection.setVisibility(View.VISIBLE);
                    tvAlert.setText(alertsText);
                } else {
                    alertSection.setVisibility(View.GONE);
                }
            }
        }).execute(city);
    }

    private class FetchWeatherTask extends AsyncTask<String, Void, String> {
        private double lat = 0.0;
        private double lon = 0.0;
        private final Callback callback;

        FetchWeatherTask(Callback callback) {
            this.callback = callback;
        }

        @Override
        protected String doInBackground(String... params) {
            String city = params[0];
            String urlString = WEATHER_URL + "?q=" + city + "&units=metric&appid=" + API_KEY;

            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.connect();

                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }

                JSONObject jsonObject = new JSONObject(result.toString());
                JSONObject coordObj = jsonObject.getJSONObject("coord");
                lat = coordObj.getDouble("lat");
                lon = coordObj.getDouble("lon");

                return result.toString();
            } catch (IOException | JSONException e) {
                Log.e("WeatherApp", "Weather fetch error: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            progressBar.setVisibility(View.GONE);
            if (result != null) {
                try {
                    JSONObject jsonObject = new JSONObject(result);
                    JSONObject mainObj = jsonObject.getJSONObject("main");
                    JSONArray weatherArray = jsonObject.getJSONArray("weather");
                    JSONObject weatherObj = weatherArray.getJSONObject(0);
                    JSONObject windObj = jsonObject.getJSONObject("wind");

                    String cityName = jsonObject.getString("name");
                    String country = jsonObject.getJSONObject("sys").getString("country");
                    double temperature = mainObj.getDouble("temp");
                    String description = weatherObj.getString("description");
                    int humidity = mainObj.getInt("humidity");
                    double windSpeed = windObj.getDouble("speed");
                    String iconCode = weatherObj.getString("icon");

                    tvCityName.setText(cityName + ", " + country);
                    tvTemperature.setText(String.format("%.1f°C", temperature));
                    tvDescription.setText(capitalizeFirstLetter(description));
                    tvHumidity.setText("Humidity: " + humidity + "%");
                    tvWind.setText("Wind: " + windSpeed + " m/s");

                    setWeatherIcon(iconCode);
                    updateBackground(iconCode);

                    callback.onCoordinatesFetched(lat, lon);
                    callback.onWeatherFetched();

                } catch (JSONException e) {
                    Log.e("WeatherApp", "JSON parsing error: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "JSON parsing error", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "Error fetching weather data", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class FetchSevereWeatherAlertsTask extends AsyncTask<Double, Void, String> {
        private final Callback callback;

        FetchSevereWeatherAlertsTask(Callback callback) {
            this.callback = callback;
        }

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
            String alertsText = "No severe weather alerts";
            if (result != null) {
                try {
                    JSONObject jsonObject = new JSONObject(result);
                    if (jsonObject.has("alerts")) {
                        JSONArray alertsArray = jsonObject.getJSONArray("alerts");
                        StringBuilder alertsBuilder = new StringBuilder();
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

                        for (int i = 0; i < alertsArray.length(); i++) {
                            JSONObject alert = alertsArray.getJSONObject(i);
                            String event = alert.getString("event");
                            long start = alert.getLong("start") * 1000;
                            long end = alert.getLong("end") * 1000;
                            String description = alert.getString("description");
                            String sender = alert.getString("sender_name");

                            alertsBuilder.append(event).append(" (")
                                    .append(sdf.format(new Date(start))).append(" to ")
                                    .append(sdf.format(new Date(end))).append(")\n")
                                    .append(description).append("\n")
                                    .append("Issued by: ").append(sender).append("\n\n");
                        }
                        alertsText = alertsBuilder.toString().trim();
                    }
                } catch (JSONException e) {
                    Log.e("WeatherApp", "Severe weather alerts parsing error: " + e.getMessage());
                    alertsText = "Error parsing severe weather alerts";
                }
            } else {
                alertsText = "Error fetching severe weather alerts";
            }

            if (callback != null) {
                callback.onSevereWeatherFetched(alertsText);
            }
        }
    }

    private class FetchForecastTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String city = params[0];
            String urlString = FORECAST_URL + "?q=" + city + "&units=metric&cnt=40&appid=" + API_KEY;

            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.connect();

                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                return result.toString();
            } catch (IOException e) {
                Log.e("WeatherApp", "Forecast fetch error: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            progressBar.setVisibility(View.GONE);
            if (result != null) {
                try {
                    forecastData = result;

                    JSONObject jsonObject = new JSONObject(result);
                    JSONArray listArray = jsonObject.getJSONArray("list");

                    SimpleDateFormat sdfDate = new SimpleDateFormat("EEEE", Locale.getDefault());
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(new Date());
                    calendar.add(Calendar.DAY_OF_MONTH, 1);
                    String[] targetDays = new String[3];
                    for (int i = 0; i < 3; i++) {
                        targetDays[i] = sdfDate.format(calendar.getTime());
                        calendar.add(Calendar.DAY_OF_MONTH, 1);
                    }

                    int forecastIndex = 0;
                    ImageView[] forecastIcons = {ivForecastIcon1, ivForecastIcon2, ivForecastIcon3};
                    TextView[] forecastViews = {tvForecast1, tvForecast2, tvForecast3};

                    for (int i = 0; i < listArray.length() && forecastIndex < 3; i++) {
                        JSONObject forecastObj = listArray.getJSONObject(i);
                        String forecastDateTime = forecastObj.getString("dt_txt");
                        String forecastDate = forecastDateTime.substring(0, 10);
                        Calendar forecastCal = Calendar.getInstance();
                        forecastCal.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(forecastDate));
                        String dayOfWeek = sdfDate.format(forecastCal.getTime());

                        if (dayOfWeek.equals(targetDays[forecastIndex])) {
                            String time = forecastDateTime.substring(11, 16);
                            double temp = forecastObj.getJSONObject("main").getDouble("temp");
                            String desc = forecastObj.getJSONArray("weather").getJSONObject(0).getString("description");
                            String iconCode = forecastObj.getJSONArray("weather").getJSONObject(0).getString("icon");
                            forecastViews[forecastIndex].setText(dayOfWeek + ": " + String.format("%.1f°C", temp) + ", " + capitalizeFirstLetter(desc));
                            setForecastIcon(forecastIcons[forecastIndex], iconCode);
                            forecastIndex++;
                        }
                    }

                    for (int i = forecastIndex; i < 3; i++) {
                        forecastViews[i].setText("Day " + (i + 1) + ": --°C, --");
                        forecastIcons[i].setImageResource(android.R.drawable.ic_menu_gallery);
                    }

                } catch (Exception e) {
                    Log.e("WeatherApp", "Forecast parsing error: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Forecast parsing error", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "Error fetching forecast data", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class FetchWeatherMapTask extends AsyncTask<Void, Void, Bitmap> {
        private final double lat;
        private final double lon;

        FetchWeatherMapTask(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            if (lat == 0.0 || lon == 0.0) {
                Log.e("WeatherApp", "Invalid coordinates: lat=" + lat + ", lon=" + lon);
                return null;
            }

            try {
                int zoom = 3;
                int xTile = (int) Math.floor((lon + 180) / 360 * Math.pow(2, zoom));
                int yTile = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * Math.pow(2, zoom));

                String urlString = String.format(MAP_URL, xTile, yTile, API_KEY);
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.connect();

                InputStream inputStream = connection.getInputStream();
                return BitmapFactory.decodeStream(inputStream);
            } catch (IOException e) {
                Log.e("WeatherApp", "Map fetch error: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            progressBar.setVisibility(View.GONE);
            if (bitmap != null) {
                ivWeatherMap.setImageBitmap(bitmap);
            } else {
                ivWeatherMap.setImageResource(android.R.drawable.ic_menu_gallery);
                Toast.makeText(MainActivity.this, "Error fetching weather map", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateBackground(String iconCode) {
        int newBackgroundRes;
        if (iconCode.startsWith("01")) {
            newBackgroundRes = R.drawable.sunny_gradient;
        } else if (iconCode.startsWith("02") || iconCode.startsWith("03") || iconCode.startsWith("04")) {
            newBackgroundRes = R.drawable.cloudy_gradient;
        } else if (iconCode.startsWith("09") || iconCode.startsWith("10")) {
            newBackgroundRes = R.drawable.rainy_gradient;
        } else if (iconCode.startsWith("11")) {
            newBackgroundRes = R.drawable.thunder_gradient;
        } else if (iconCode.startsWith("13")) {
            newBackgroundRes = R.drawable.snowy_gradient;
        } else {
            newBackgroundRes = R.drawable.default_gradient;
        }

        animateBackgroundTransition(newBackgroundRes);
    }

    private void animateBackgroundTransition(int newBackgroundRes) {
        backgroundNew.setBackgroundResource(newBackgroundRes);

        ValueAnimator fadeInAnimator = ValueAnimator.ofFloat(0f, 1f);
        fadeInAnimator.setDuration(1000);
        fadeInAnimator.addUpdateListener(animation -> {
            float alpha = (float) animation.getAnimatedValue();
            backgroundNew.setAlpha(alpha);
            backgroundOld.setAlpha(1f - alpha);
        });

        fadeInAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                backgroundOld.setBackground(backgroundNew.getBackground());
                backgroundOld.setAlpha(1f);
                backgroundNew.setAlpha(0f);
            }
        });

        fadeInAnimator.start();
    }

    private void setWeatherIcon(String iconCode) {
        int resourceId;
        if (iconCode.startsWith("01")) { // Clear sky
            resourceId = R.drawable.sun_icon;
        } else if (iconCode.startsWith("02") || iconCode.startsWith("03") || iconCode.startsWith("04")) { // Clouds
            resourceId = R.drawable.cloud_icon;
        } else if (iconCode.startsWith("09") || iconCode.startsWith("10")) { // Rain
            resourceId = R.drawable.rain_icon;
        } else if (iconCode.startsWith("11")) { // Thunderstorm
            resourceId = R.drawable.thunder_icon;
        } else if (iconCode.startsWith("13")) { // Snow
            resourceId = R.drawable.snow_icon;
        } else if (iconCode.startsWith("50")) { // Mist
            resourceId = R.drawable.mist_icon;
        } else {
            resourceId = R.drawable.sun_icon; // Default to sun
        }
        ivWeatherIcon.setImageResource(resourceId);
    }

    private void setForecastIcon(ImageView imageView, String iconCode) {
        int resourceId;
        if (iconCode.startsWith("01")) { // Clear sky
            resourceId = R.drawable.sun_icon;
        } else if (iconCode.startsWith("02") || iconCode.startsWith("03") || iconCode.startsWith("04")) { // Clouds
            resourceId = R.drawable.cloud_icon;
        } else if (iconCode.startsWith("09") || iconCode.startsWith("10")) { // Rain
            resourceId = R.drawable.rain_icon;
        } else if (iconCode.startsWith("11")) { // Thunderstorm
            resourceId = R.drawable.thunder_icon;
        } else if (iconCode.startsWith("13")) { // Snow
            resourceId = R.drawable.snow_icon;
        } else if (iconCode.startsWith("50")) { // Mist
            resourceId = R.drawable.mist_icon;
        } else {
            resourceId = R.drawable.sun_icon; // Default to sun
        }
        imageView.setImageResource(resourceId);
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

    private String getDayFromForecast(TextView forecastView) {
        String text = forecastView.getText().toString();
        return text.split(":")[0].trim();
    }

    private void openHourlyForecastWithDay(String day) {
        String city = etCityName.getText().toString().trim();
        if (city.isEmpty() && tvCityName.getText().toString().contains(",")) {
            city = tvCityName.getText().toString().split(",")[0].trim();
        }
        if (!city.isEmpty() && forecastData != null) {
            try {
                Intent intent = new Intent(MainActivity.this, HourlyForecastActivity.class);
                intent.putExtra("city", city);
                intent.putExtra("forecast_data", forecastData);
                intent.putExtra("selected_day", day);
                startActivity(intent);
            } catch (Exception e) {
                Log.e("WeatherApp", "Error launching HourlyForecastActivity: " + e.getMessage());
                Toast.makeText(MainActivity.this, "Failed to open hourly forecast", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(MainActivity.this, "Forecast data not available. Please search again.", Toast.LENGTH_SHORT).show();
        }
    }
}