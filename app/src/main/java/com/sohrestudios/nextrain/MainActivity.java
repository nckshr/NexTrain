package com.sohrestudios.nextrain;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "NexTrain";
    private String route;
    private String stop;
    private String direction;
    private refreshTimer refresher;
    private CountDownTimer counter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        refresher = new refreshTimer(30000,30000);
        Spinner routeSelect = (Spinner) findViewById(R.id.route_select);
        Spinner dirSelect = (Spinner) findViewById(R.id.direction_select);
        Spinner stopSelect = (Spinner) findViewById(R.id.stop_select);
        routeSelect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                StringTag s = (StringTag) parent.getItemAtPosition(position);
                String routeID = (String)s.tag;
                route = routeID;
                try {
                    URL url = new URL("http://svc.metrotransit.org/NexTrip/Directions/"+route);
                    DirectionGetter dirGetter = new DirectionGetter();
                    dirGetter.execute(url);
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        dirSelect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                StringTag s = (StringTag) parent.getItemAtPosition(position);
                String dirID = (String)s.tag;
                direction = dirID;
                try {
                    URL url = new URL("http://svc.metrotransit.org/NexTrip/Stops/"+route+"/"+direction);
                    StopGetter stopGetter = new StopGetter();
                    stopGetter.execute(url);
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        stopSelect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                StringTag s = (StringTag) parent.getItemAtPosition(position);
                String stopID = (String)s.tag;
                stop = stopID;
                refresher.cancel();
                Log.d(TAG, "cancelling refresher");
                updateNextTrip();
                Log.d(TAG, "restarting refresher");
                refresher.start();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        try {
            URL url = new URL("http://svc.metrotransit.org/NexTrip/Routes");
            RouteGetter request = new RouteGetter();
            request.execute(url);
        }catch(Exception e){
            //failed
        }
    }
    public void updateNextTrip(){
        try {
            URL url = new URL("http://svc.metrotransit.org/NexTrip/"+route+"/"+direction+"/"+stop);
            NexTripGetter tripGetter = new NexTripGetter();
            tripGetter.execute(url);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }
    public void onRefreshClick(View v){
        updateNextTrip();
    }
    public class StringTag {
        public String string;
        public Object tag;

        public StringTag(String stringPart, Object tagPart) {
            string = stringPart;
            tag = tagPart;
        }

        @Override
        public String toString() {
            return string;
        }
    }
    private class RouteGetter extends AsyncTask<URL, Integer, String[]> {
        protected String[] doInBackground(URL... urls) {
            int count = urls.length;
            long totalSize = 0;
            StringBuilder result;
            String[] results = new String[urls.length];
            for (int i = 0; i < count; i++) {
                result = new StringBuilder();
                try {
                    URL url = urls[i];
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                    byte[] buf = new byte[1];
                    while(in.read(buf) != -1){
                        result.append(new String(buf));
                    }
                    urlConnection.disconnect();
                    results[i] = result.toString();
                    publishProgress(i);
                }
                catch(Exception e) {
                    //failed
                    Log.d(TAG, "failed to get url: " + e.getMessage());
                }
            }
            return results;
        }

        protected void onProgressUpdate(Integer... progress) {
            //do nothing
        }

        protected void onPostExecute(String[] results) {
            Log.d(TAG,"RouteGetter onPost");
            String docStr = results[0];
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = null;
            List<StringTag> routeItems = new ArrayList<StringTag>();
            try {
                builder = factory.newDocumentBuilder();
                Document document = builder.parse(new InputSource(new StringReader(docStr)));
                NodeList nodeList = document.getDocumentElement().getElementsByTagName("NexTripRoute");
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Node node = nodeList.item(i);
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        Element elem = (Element) node;
                        String routeDesc = elem.getElementsByTagName("Description").item(0).getChildNodes().item(0).getNodeValue();
                        String routeID = elem.getElementsByTagName("Route").item(0).getChildNodes().item(0).getNodeValue();
                        routeItems.add(new StringTag(routeDesc, routeID));
                    }
                }
                //Populate spinners
                Spinner spinner = (Spinner) findViewById(R.id.route_select);
                // Create an ArrayAdapter using the string array and a default spinner layout
                ArrayAdapter<StringTag> adapter = new ArrayAdapter<StringTag>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, routeItems);
                // Apply the adapter to the spinner
                spinner.setAdapter(adapter);
            }catch (Exception e) {
                Log.d(TAG,"failed to parse xml document:" + e.getMessage());
            }
        }
    }
    private class DirectionGetter extends AsyncTask<URL, Integer, String[]> {
        protected String[] doInBackground(URL... urls) {
            int count = urls.length;
            long totalSize = 0;
            StringBuilder result;
            String[] results = new String[urls.length];
            for (int i = 0; i < count; i++) {
                result = new StringBuilder();
                try {
                    URL url = urls[i];
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                    byte[] buf = new byte[1];
                    while(in.read(buf) != -1){
                        result.append(new String(buf));
                    }
                    urlConnection.disconnect();
                    results[i] = result.toString();
                    publishProgress(i);
                }
                catch(Exception e) {
                    //failed
                    Log.d(TAG, "failed to get url: " + e.getMessage());
                }
            }
            return results;
        }

        protected void onProgressUpdate(Integer... progress) {
            //do nothing
        }

        protected void onPostExecute(String[] results) {
            Log.d(TAG,"DirectionGetter onPost");
            String docStr = results[0];
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = null;
            List<StringTag> dirItems = new ArrayList<StringTag>();
            try {
                builder = factory.newDocumentBuilder();
                Document document = builder.parse(new InputSource(new StringReader(docStr)));
                NodeList nodeList = document.getDocumentElement().getElementsByTagName("TextValuePair");
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Node node = nodeList.item(i);
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        Element elem = (Element) node;
                        String dirDesc = elem.getElementsByTagName("Text").item(0).getChildNodes().item(0).getNodeValue();
                        String dirID = elem.getElementsByTagName("Value").item(0).getChildNodes().item(0).getNodeValue();
                        dirItems.add(new StringTag(dirDesc, dirID));
                    }
                }
                //Populate spinners
                Spinner spinner = (Spinner) findViewById(R.id.direction_select);
                // Create an ArrayAdapter using the string array and a default spinner layout
                ArrayAdapter<StringTag> adapter = new ArrayAdapter<StringTag>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, dirItems);
                // Apply the adapter to the spinner
                spinner.setAdapter(adapter);
            }catch (Exception e) {
                Log.d(TAG,"failed to parse xml document:" + e.getMessage());
            }
        }
    }
    private class StopGetter extends AsyncTask<URL, Integer, String[]> {
        protected String[] doInBackground(URL... urls) {
            int count = urls.length;
            long totalSize = 0;
            StringBuilder result;
            String[] results = new String[urls.length];
            for (int i = 0; i < count; i++) {
                result = new StringBuilder();
                try {
                    URL url = urls[i];
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                    byte[] buf = new byte[1];
                    while(in.read(buf) != -1){
                        result.append(new String(buf));
                    }
                    urlConnection.disconnect();
                    results[i] = result.toString();
                    publishProgress(i);
                }
                catch(Exception e) {
                    //failed
                    Log.d(TAG, "failed to get url: " + e.getMessage());
                }
            }
            return results;
        }

        protected void onProgressUpdate(Integer... progress) {
            //do nothing
        }

        protected void onPostExecute(String[] results) {
            Log.d(TAG,"StopGetter onPost");
            String docStr = results[0];
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = null;
            List<StringTag> stopItems = new ArrayList<StringTag>();
            try {
                builder = factory.newDocumentBuilder();
                Document document = builder.parse(new InputSource(new StringReader(docStr)));
                NodeList nodeList = document.getDocumentElement().getElementsByTagName("TextValuePair");
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Node node = nodeList.item(i);
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        Element elem = (Element) node;
                        String stopDesc = elem.getElementsByTagName("Text").item(0).getChildNodes().item(0).getNodeValue();
                        String stopID = elem.getElementsByTagName("Value").item(0).getChildNodes().item(0).getNodeValue();
                        stopItems.add(new StringTag(stopDesc, stopID));
                    }
                }
                //Populate spinners
                Spinner spinner = (Spinner) findViewById(R.id.stop_select);
                // Create an ArrayAdapter using the string array and a default spinner layout
                ArrayAdapter<StringTag> adapter = new ArrayAdapter<StringTag>(MainActivity.this, android.R.layout.simple_spinner_dropdown_item, stopItems);
                // Apply the adapter to the spinner
                spinner.setAdapter(adapter);
            }catch (Exception e) {
                Log.d(TAG,"failed to parse xml document:" + e.getMessage());
            }
        }
    }
    private class NexTripGetter extends AsyncTask<URL, Integer, String[]> {
        protected String[] doInBackground(URL... urls) {
            int count = urls.length;
            long totalSize = 0;
            StringBuilder result;
            String[] results = new String[urls.length];
            for (int i = 0; i < count; i++) {
                result = new StringBuilder();
                try {
                    URL url = urls[i];
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                    byte[] buf = new byte[1];
                    while(in.read(buf) != -1){
                        result.append(new String(buf));
                    }
                    urlConnection.disconnect();
                    results[i] = result.toString();
                    publishProgress(i);
                }
                catch(Exception e) {
                    //failed
                    Log.d(TAG, "failed to get url: " + e.getMessage());
                }
            }
            return results;
        }

        protected void onProgressUpdate(Integer... progress) {
            //do nothing
        }

        protected void onPostExecute(String[] results) {
            Log.d(TAG,"NexTripGetter onPost");
            String docStr = results[0];
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = null;
            try {
                builder = factory.newDocumentBuilder();
                Document document = builder.parse(new InputSource(new StringReader(docStr)));
                NodeList nodeList = document.getDocumentElement().getElementsByTagName("NexTripDeparture");
                ArrayList<Integer> depTimes = new ArrayList<Integer>();
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Node node = nodeList.item(i);
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        Element elem = (Element) node;
                        String tripTime = elem.getElementsByTagName("DepartureTime").item(0).getChildNodes().item(0).getNodeValue();
                        String tripDesc = elem.getElementsByTagName("Description").item(0).getChildNodes().item(0).getNodeValue();
                        DateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
                        Date departure = fmt.parse(tripTime);
                        Date now = new Date();
                        int minutesLeft = (int)Math.floor((departure.getTime() - now.getTime())/(1000*60));
                        depTimes.add(minutesLeft);
                    }
                }
                //update UI
                Collections.sort(depTimes);
                int minutesLeft = depTimes.get(0);
                if(counter != null) {
                    counter.cancel();
                }
                counter = new CountDownTimer(minutesLeft*1000*60, 1000) {

                    public void onTick(long millisUntilFinished) {
                        ((TextView)findViewById(R.id.countdown)).setText(String.format("%02d", millisUntilFinished/(1000*60)) + ":" + String.format("%02d",(millisUntilFinished / 1000) % 60));
                    }

                    public void onFinish() {
                        ((TextView)findViewById(R.id.countdown)).setText("DUE");
                    }
                };
                counter.start();
            }catch (Exception e) {
                Log.d(TAG,"failed to parse xml document:" + e.getMessage());
            }
        }
    }
    public class refreshTimer extends CountDownTimer{
        private long repeat;
        private long tickInterval;
        /**
         * @param millisInFuture    The number of millis in the future from the call
         *                          to {@link #start()} until the countdown is done and {@link #onFinish()}
         *                          is called.
         * @param countDownInterval The interval along the way to receive
         *                          {@link #onTick(long)} callbacks.
         */
        public refreshTimer(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
            repeat = millisInFuture;
            tickInterval = countDownInterval;
        }

        public void onTick(long millisUntilFinished) {
            //do nothing
        }
        public void onFinish() {
            Log.d(TAG,"updating countdown...");
            updateNextTrip();
            (new refreshTimer(repeat,tickInterval)).start();
        }
    }
}
