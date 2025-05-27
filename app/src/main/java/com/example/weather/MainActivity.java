package com.example.weather;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText; // Using EditText as per your original structure
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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
    private final String API_KEY = "dad72bbf3ec207a8585ff3b7dcbcf9a8"; // Replace with your API key
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.my_app_green));
        }

        setContentView(R.layout.activity_main);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Adjust status bar placeholder height dynamically
        int statusBarHeight = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId);
        }
        View statusBarPlaceholder = findViewById(R.id.statusBarPlaceholder);
        ViewGroup.LayoutParams params = statusBarPlaceholder.getLayoutParams();
        params.height = statusBarHeight;
        statusBarPlaceholder.setLayoutParams(params);
        // statusBarPlaceholder.setBackgroundColor(ContextCompat.getColor(this, R.color.my_app_green)); // Optional: Match status bar

        // Initialize UI components
        etCityName = findViewById(R.id.etCityName); // This should find an EditText in your layout
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
                if (city.matches("[a-zA-Z\\s,]+")) { // Original regex
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString(LAST_CITY_KEY, city);
                    editor.apply();

                    progressBar.setVisibility(View.VISIBLE);
                    fetchWeatherData(city);
                } else {
                    Toast.makeText(MainActivity.this, R.string.invalid_city_name, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, R.string.enter_city_name, Toast.LENGTH_SHORT).show();
            }
        });

        // Make weather icon clickable
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
                        Log.e("WeatherApp", "Error launching HourlyForecastActivity: " + e.getMessage(), e);
                        Toast.makeText(MainActivity.this, R.string.error_hourly_forecast, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, R.string.no_forecast_data, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, R.string.enter_city_name_first, Toast.LENGTH_SHORT).show();
            }
        });

        // Add click listeners for forecast icons
        ivForecastIcon1.setOnClickListener(v -> openHourlyForecastWithDay(getDayFromForecast(tvForecast1)));
        ivForecastIcon2.setOnClickListener(v -> openHourlyForecastWithDay(getDayFromForecast(tvForecast2)));
        ivForecastIcon3.setOnClickListener(v -> openHourlyForecastWithDay(getDayFromForecast(tvForecast3)));
    }

    private void fetchWeatherData(String city) {
        new FetchWeatherTask(new Callback() {
            @Override
            public void onCoordinatesFetched(double lat, double lon) {
                new FetchWeatherMapTask(lat, lon).execute();
                new FetchSevereWeatherAlertsTask(this).execute(lat, lon); // Pass 'this' (Callback)
            }

            @Override
            public void onWeatherFetched() {
                new FetchForecastTask().execute(city);
            }

            @Override
            public void onSevereWeatherFetched(String alertsText) {
                if (alertsText != null && !alertsText.equals("No severe weather alerts") &&
                        !alertsText.equals(getString(R.string.error_fetching_alerts)) && // Compare with string resource
                        !alertsText.equals(getString(R.string.error_parsing_alerts))) {  // Compare with string resource
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

            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.connect();

                if (connection.getResponseCode() != 200) {
                    Log.e("WeatherApp", "Weather fetch failed: " + connection.getResponseCode() + " for city: " + city);
                    return null;
                }

                InputStream inputStream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }

                // Parse coordinates here to ensure they are available for the callback
                JSONObject jsonObject = new JSONObject(result.toString());
                if (jsonObject.has("coord")) {
                    JSONObject coordObj = jsonObject.getJSONObject("coord");
                    lat = coordObj.getDouble("lat");
                    lon = coordObj.getDouble("lon");
                }
                return result.toString();
            } catch (IOException | JSONException e) {
                Log.e("WeatherApp", "Weather fetch error for " + city + ": " + e.getMessage(), e);
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.e("WeatherApp", "Error closing reader", e);
                    }
                }
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

                    tvCityName.setText(String.format("%s, %s", cityName, country));
                    tvTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", temperature));
                    tvDescription.setText(capitalizeFirstLetter(description));
                    tvHumidity.setText(getString(R.string.humidity_template, humidity));
                    tvWind.setText(getString(R.string.wind_template, windSpeed));

                    setWeatherIcon(iconCode);
                    updateBackground(iconCode);

                    if (lat != 0.0 || lon != 0.0) {
                        callback.onCoordinatesFetched(lat, lon);
                    }
                    callback.onWeatherFetched();

                } catch (JSONException e) {
                    Log.e("WeatherApp", "JSON parsing error: " + e.getMessage(), e);
                    Toast.makeText(MainActivity.this, R.string.json_parsing_error, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, R.string.error_fetching_weather, Toast.LENGTH_SHORT).show();
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
            if (params.length < 2 || params[0] == null || params[1] == null || (params[0] == 0.0 && params[1] == 0.0)) {
                Log.e("WeatherApp", "Invalid coordinates for severe weather alerts.");
                return getString(R.string.error_fetching_alerts); // Use string resource
            }
            double lat = params[0];
            double lon = params[1];
            String urlString = ONECALL_URL + "?lat=" + lat + "&lon=" + lon + "&exclude=hourly,daily,minutely,current&appid=" + API_KEY;

            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.connect();

                if (connection.getResponseCode() != 200) {
                    Log.e("WeatherApp", "Severe weather fetch failed: " + connection.getResponseCode());
                    return getString(R.string.error_fetching_alerts); // Use string resource
                }

                InputStream inputStream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            } catch (IOException e) {
                Log.e("WeatherApp", "Severe weather alerts fetch error: " + e.getMessage(), e);
                return getString(R.string.error_fetching_alerts); // Use string resource
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.e("WeatherApp", "Error closing reader", e);
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(String result) {
            String alertsText = "No severe weather alerts";
            if (result != null && !result.equals(getString(R.string.error_fetching_alerts))) { // Check against resource
                try {
                    JSONObject jsonObject = new JSONObject(result);
                    if (jsonObject.has("alerts")) {
                        JSONArray alertsArray = jsonObject.getJSONArray("alerts");
                        if (alertsArray.length() > 0) { // Check if there are actual alerts
                            StringBuilder alertsBuilder = new StringBuilder();
                            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

                            for (int i = 0; i < alertsArray.length(); i++) {
                                JSONObject alert = alertsArray.getJSONObject(i);
                                String event = alert.getString("event");
                                long start = alert.getLong("start") * 1000;
                                long end = alert.getLong("end") * 1000;
                                String description = alert.optString("description", "No description provided.");
                                String sender = alert.optString("sender_name", "Unknown source");

                                alertsBuilder.append(event).append(" (")
                                        .append(sdf.format(new Date(start))).append(" to ")
                                        .append(sdf.format(new Date(end))).append(")\n")
                                        .append(description).append("\n")
                                        .append("Issued by: ").append(sender).append("\n\n");
                            }
                            alertsText = alertsBuilder.toString().trim();
                        }
                        // If alerts array is empty, "No severe weather alerts" is correct.
                    }
                } catch (JSONException e) {
                    Log.e("WeatherApp", "Severe weather alerts parsing error: " + e.getMessage(), e);
                    alertsText = getString(R.string.error_parsing_alerts); // Use string resource
                }
            } else if (result != null) { // This means result is an error string from doInBackground
                alertsText = result; // This would be R.string.error_fetching_alerts
            }
            // If result was null (shouldn't happen with current doInBackground returning error string)
            // it defaults to "No severe weather alerts"

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

            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.connect();

                if (connection.getResponseCode() != 200) {
                    Log.e("WeatherApp", "Forecast fetch failed: " + connection.getResponseCode());
                    return null;
                }

                InputStream inputStream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                return result.toString();
            } catch (IOException e) {
                Log.e("WeatherApp", "Forecast fetch error: " + e.getMessage(), e);
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.e("WeatherApp", "Error closing reader", e);
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(String result) {
            // progressBar.setVisibility(View.GONE); // Usually handled by main weather task
            if (result != null) {
                try {
                    forecastData = result; // Store raw forecast data

                    JSONObject jsonObject = new JSONObject(result);
                    JSONArray listArray = jsonObject.getJSONArray("list");

                    SimpleDateFormat sdfDayName = new SimpleDateFormat("EEEE", Locale.getDefault());
                    SimpleDateFormat sdfParseDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

                    java.util.List<JSONObject> dailyDisplayForecasts = new java.util.ArrayList<>();
                    java.util.List<String> processedDays = new java.util.ArrayList<>();

                    Calendar todayCal = Calendar.getInstance();
                    String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(todayCal.getTime());

                    for (int i = 0; i < listArray.length() && dailyDisplayForecasts.size() < 3; i++) {
                        JSONObject forecastEntry = listArray.getJSONObject(i);
                        String dt_txt = forecastEntry.getString("dt_txt");
                        Date forecastDate = sdfParseDateTime.parse(dt_txt);
                        String entryDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(forecastDate);

                        if (entryDateStr.equals(todayDateStr) || processedDays.contains(entryDateStr)) {
                            continue;
                        }
                        if (dt_txt.contains("12:00:00") || dt_txt.contains("15:00:00") || (processedDays.isEmpty() || !processedDays.get(processedDays.size()-1).equals(entryDateStr))) {
                            dailyDisplayForecasts.add(forecastEntry);
                            processedDays.add(entryDateStr);
                        }
                    }
                    if (dailyDisplayForecasts.size() < 3) {
                        dailyDisplayForecasts.clear();
                        processedDays.clear();
                        for (int i = 0; i < listArray.length() && dailyDisplayForecasts.size() < 3; i++) {
                            JSONObject forecastEntry = listArray.getJSONObject(i);
                            String dt_txt = forecastEntry.getString("dt_txt");
                            Date forecastDate = sdfParseDateTime.parse(dt_txt);
                            String entryDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(forecastDate);

                            if (entryDateStr.equals(todayDateStr) || processedDays.contains(entryDateStr)) {
                                continue;
                            }
                            dailyDisplayForecasts.add(forecastEntry);
                            processedDays.add(entryDateStr);
                        }
                    }


                    TextView[] forecastViews = {tvForecast1, tvForecast2, tvForecast3};
                    ImageView[] forecastIcons = {ivForecastIcon1, ivForecastIcon2, ivForecastIcon3};

                    for (int i = 0; i < 3; i++) {
                        if (i < dailyDisplayForecasts.size()) {
                            JSONObject dayForecast = dailyDisplayForecasts.get(i);
                            Date forecastDate = sdfParseDateTime.parse(dayForecast.getString("dt_txt"));
                            String dayName = sdfDayName.format(forecastDate);
                            double temp = dayForecast.getJSONObject("main").getDouble("temp");
                            String desc = dayForecast.getJSONArray("weather").getJSONObject(0).getString("description");
                            String iconCode = dayForecast.getJSONArray("weather").getJSONObject(0).getString("icon");

                            forecastViews[i].setText(String.format(Locale.getDefault(), "%s: %.1f°C, %s", dayName, temp, capitalizeFirstLetter(desc)));
                            setForecastIcon(forecastIcons[i], iconCode);
                        } else {
                            forecastViews[i].setText(String.format("Day %d: --°C, --", i + 1));
                            forecastIcons[i].setImageResource(android.R.drawable.ic_menu_gallery);
                        }
                    }

                } catch (Exception e) {
                    Log.e("WeatherApp", "Forecast parsing error: " + e.getMessage(), e);
                    Toast.makeText(MainActivity.this, R.string.forecast_parsing_error, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, R.string.error_fetching_forecast, Toast.LENGTH_SHORT).show();
                TextView[] forecastViews = {tvForecast1, tvForecast2, tvForecast3};
                ImageView[] forecastIcons = {ivForecastIcon1, ivForecastIcon2, ivForecastIcon3};
                for (int i = 0; i < 3; i++) {
                    forecastViews[i].setText(String.format("Day %d: --°C, --", i + 1));
                    forecastIcons[i].setImageResource(android.R.drawable.ic_menu_gallery);
                }
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
            if (lat == 0.0 && lon == 0.0) {
                Log.e("WeatherApp", "Invalid coordinates for map: lat=" + lat + ", lon=" + lon);
                return null;
            }
            HttpURLConnection connection = null;
            try {
                int zoom = 3;
                int xTile = (int) Math.floor((lon + 180.0) / 360.0 * Math.pow(2, zoom));
                int yTile = (int) Math.floor((1.0 - Math.log(Math.tan(Math.toRadians(lat)) + 1.0 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2.0 * Math.pow(2, zoom));

                String urlString = String.format(Locale.US, MAP_URL, xTile, yTile, API_KEY);
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.connect();

                if (connection.getResponseCode() != 200) {
                    Log.e("WeatherApp", "Map fetch failed with code: " + connection.getResponseCode() + " for URL: " + urlString);
                    return null;
                }
                InputStream inputStream = connection.getInputStream();
                return BitmapFactory.decodeStream(inputStream);
            } catch (IOException e) {
                Log.e("WeatherApp", "Map fetch error: " + e.getMessage(), e);
                return null;
            } catch (Exception e) {
                Log.e("WeatherApp", "Unexpected error in FetchWeatherMapTask: " + e.getMessage(), e);
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                ivWeatherMap.setImageBitmap(bitmap);
            } else {
                ivWeatherMap.setImageResource(android.R.drawable.ic_menu_mapmode);
                // Toast.makeText(MainActivity.this, R.string.error_fetching_map, Toast.LENGTH_SHORT).show(); // Optional
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
        ivWeatherIcon.setImageResource(getIconResource(iconCode));
    }

    private void setForecastIcon(ImageView imageView, String iconCode) {
        imageView.setImageResource(getIconResource(iconCode));
    }

    private int getIconResource(String iconCode) {
        if (iconCode == null) return R.drawable.sun_icon;
        if (iconCode.startsWith("01")) return R.drawable.sun_icon;
        if (iconCode.startsWith("02") || iconCode.startsWith("03") || iconCode.startsWith("04")) return R.drawable.cloud_icon;
        if (iconCode.startsWith("09") || iconCode.startsWith("10")) return R.drawable.rain_icon;
        if (iconCode.startsWith("11")) return R.drawable.thunder_icon;
        if (iconCode.startsWith("13")) return R.drawable.snow_icon;
        if (iconCode.startsWith("50")) return R.drawable.mist_icon;
        return R.drawable.sun_icon;
    }

    private String capitalizeFirstLetter(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.substring(0, 1).toUpperCase(Locale.getDefault()) + text.substring(1);
    }

    private String getDayOfWeek(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE", Locale.getDefault());
        return sdf.format(date);
    }

    private String getDayFromForecast(TextView forecastView) {
        String text = forecastView.getText().toString();
        if (text.contains(":")) {
            return text.split(":")[0].trim();
        }
        Log.w("WeatherApp", "Could not parse day from forecast TextView: " + text);
        return getDayOfWeek(new Date());
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
                Log.e("WeatherApp", "Error launching HourlyForecastActivity for day " + day + ": " + e.getMessage(), e);
                Toast.makeText(MainActivity.this, R.string.error_hourly_forecast, Toast.LENGTH_SHORT).show();
            }
        } else {
            if (city.isEmpty()) {
                Toast.makeText(MainActivity.this, R.string.enter_city_name_first, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, R.string.no_forecast_data, Toast.LENGTH_SHORT).show();
            }
        }
    }
}