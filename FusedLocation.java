import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.Calendar;

/**
    Interface for using Google's FusedLocationAPI.
*/
public class FusedLocation implements ConnectionCallbacks, OnConnectionFailedListener, LocationListener {
    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    public static final long UPDATE_INTERVAL_IN_MILLISECONDS = 1000;
    /**
     * The fastest rate for active location updates. Exact. Updates will never be more frequent
     * than this value.
     */
    public static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2;
    private final static String TAG = "FUSED LOCATION";

    public Callback mCallback = null;
    /**
     * Stores parameters for requests to the FusedLocationProviderApi.
     */
    protected LocationRequest mLocationRequest;
    /**
     * Provides the entry point to Google Play services.
     */
    private GoogleApiClient mGoogleApiClient;
    private Context mContext;
    private Location mCurrentLocation = null;
    private int PRIORITY = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
    private boolean inProgress = false;
    private int numTries = 0;
    private long diffTime = 5000;
    private float minAccuracy = 35;
    private int maxTries = 1;
    private boolean lastKnownLocation = false;

    public FusedLocation(Context c, Callback callback) {
        this.mContext = c;
        this.mCallback = callback;
    }
    
    /**
      Sets up FusedLocation Updates and chooses the best reading among `maxTries` samples.
      @param maxTries Maximum number of times to sample GPS readings
    */
    public void getCurrentLocation(int maxTries) {
        this.maxTries = maxTries;
        chooseNetworkGps();
        buildGoogleApiClient();
        lastKnownLocation = false;
        inProgress = true;
        if (canGetLocation())
            mGoogleApiClient.connect();
    }
    
    /**
      Checks FusedLocation's last known location and checks to see if the the time the location was sampled
      and the accuracy of the sample is within specified limits. 
      Otherwise, it sets up fusedlocation updates and tries to get a fresh gps sample.
      @param diffTime - The maximum time, in milliseconds, that may have elapsed since the last sample.
      @param minAccuracy - The minimum accuracy, in meters, that is required of the last sample.
    */
    
    public void getLastKnownLocation(long diffTime, float minAccuracy) {
        this.diffTime = diffTime;
        this.minAccuracy = minAccuracy;
        chooseNetworkGps();
        buildGoogleApiClient();
        lastKnownLocation = true;
        inProgress = true;
        if (canGetLocation())
            mGoogleApiClient.connect();
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    protected synchronized void buildGoogleApiClient() {
        Log.i(TAG, "Building GoogleApiClient");
        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        createLocationRequest();
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();

        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);

        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);

        mLocationRequest.setPriority(PRIORITY);
    }

    /**
     * Requests location updates from the FusedLocationApi.
     */
    public void startLocationUpdates() {
        // The final argument to {@code requestLocationUpdates()} is a LocationListener
        // (http://developer.android.com/reference/com/google/android/gms/location/LocationListener.html).

        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    public void stopLocationUpdates() {
        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.

        // The final argument to {@code requestLocationUpdates()} is a LocationListener
        // (http://developer.android.com/reference/com/google/android/gms/location/LocationListener.html).
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);

        reset();
    }

    /**
     * Callback that fires when the location changes.
     */
    @Override
    public void onLocationChanged(Location location) {
        numTries++;
        if (mCurrentLocation == null)
            mCurrentLocation = location;
        else if (mCurrentLocation.getAccuracy() > location.getAccuracy()) {
            mCurrentLocation = location;
        }
        if (numTries >= maxTries) {
            mCallback.onLocationResult(mCurrentLocation);
            stopLocationUpdates();
        } else {
            chooseNetworkGps();
            //Toast.makeText(mContext, "latitude: "+mCurrentLocation.getLatitude()+" Longitude: "+mCurrentLocation.getLongitude(), Toast.LENGTH_SHORT).show();
        }

    }

    public Location getLocation(int maxtries) {
        if (numTries >= maxtries)
            return mCurrentLocation;
        else
            return null;
    }

    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "Connected to GoogleApiClient");

        // If the initial location was never previously requested, we use
        // FusedLocationApi.getLastKnownLocation() to get it. If it was previously requested, we store
        // its value in the Bundle and check for it in onCreate(). We
        // do not request it again unless the user specifically requests location updates by pressing
        // the Start Updates button.
        //
        if (lastKnownLocation) {
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if ((mCurrentLocation.getTime() - Calendar.getInstance().getTime().getTime()) < diffTime
                    && mCurrentLocation.getAccuracy() <= minAccuracy) {
                mCallback.onLocationResult(mCurrentLocation);
                reset();
            } else {
                startLocationUpdates();
            }
        } else {
            startLocationUpdates();
        }

    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }

    /**
     * Function to show settings alert dialog
     * On pressing Settings button will lauch Settings Options
     */
    public void showSettingsAlert() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(mContext);

        // Setting Dialog Title
        alertDialog.setTitle("GPS settings");

        // Setting Dialog Message
        alertDialog.setMessage("GPS is not enabled. " +
                "This app uses GPS. Do you want to go to settings menu?");

        // On pressing Settings button
        alertDialog.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                mContext.startActivity(intent);
            }
        });

        // on pressing cancel button
        alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        // Showing Alert Message
        alertDialog.show();
    }

    public void reset() {
        numTries = 0;
        mCurrentLocation = null;
        inProgress = false;
        mGoogleApiClient.disconnect();
    }

    public boolean canGetLocation() {
        return isNetworkEnabled() || isGPSEnabled();
    }

    public boolean isNetworkEnabled() {
        return ((LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE)).isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    public boolean isGPSEnabled() {
        return ((LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE)).isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    private void chooseNetworkGps() {
        if (isGPSEnabled()) {
            PRIORITY = LocationRequest.PRIORITY_HIGH_ACCURACY;
        } else if (isNetworkEnabled()) {
            PRIORITY = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
        } else {
            PRIORITY = LocationRequest.PRIORITY_NO_POWER;
        }
    }

    public boolean isInProgress() {
        return inProgress;
    }

    interface Callback {
        public void onLocationResult(Location location);
    }
}
