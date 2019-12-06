/*===============================================================================
Copyright (c) 2019 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/


package com.mattrayner.vuforia.app;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.ObjectTracker;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.State;
import com.vuforia.Trackable;
import com.vuforia.TrackableList;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;
import com.mattrayner.vuforia.app.SampleActivityBase;
import com.mattrayner.vuforia.app.SampleApplicationControl;
import com.mattrayner.vuforia.app.SampleApplicationException;
import com.mattrayner.vuforia.app.SampleApplicationSession;
import com.mattrayner.vuforia.app.utils.LoadingDialogHandler;
import com.mattrayner.vuforia.app.utils.SampleApplicationGLView;

import com.mattrayner.vuforia.VuforiaPlugin;

import java.util.ArrayList;

/**
 * The main activity for the ImageTargets detection.
 * Image Targets allows users to create 2D targets for detection and tracking
 *
 * This class does high-level handling of the Vuforia lifecycle and any UI updates
 *
 * For ImageTarget-specific rendering, check out ImageTargetRenderer.java
 * For the low-level Vuforia lifecycle code, check out SampleApplicationSession.java
 */
public class ImageTargets extends SampleActivityBase implements SampleApplicationControl
{
    private static final String LOGTAG = "ImageTargets";
    private static final String FILE_PROTOCOL = "file://";

    private SampleApplicationSession vuforiaAppSession;
    
    private DataSet mCurrentDataset;
    private final ArrayList<String> mDatasetStrings = new ArrayList<>();

    private SampleApplicationGLView mGlView;

    private ImageTargetRenderer mRenderer;

    private GestureDetector mGestureDetector;
    
    private RelativeLayout mUILayout;

    private ActionReceiver vuforiaActionReceiver;
    
    final LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(this);
    
    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;
    
    private boolean mIsDroidDevice = false;

    // Array of target names
    String mTargets;

    // Overlay message string
    String mOverlayMessage;

    // Display button boolean
    Boolean mDisplayCloseButton;

    // Display devices icon image
    Boolean mDisplayDevicesIcon;

    // Stop the activity
    Boolean mAutostopOnImageFound;

    // Vuforia license key
    String mLicenseKey;

   

    //Receives broadcasts - acts on those received from our CordovaPlugin
    private class ActionReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context ctx, Intent intent) {
            Bundle bundle = intent.getExtras();
            String receivedAction = bundle != null ? bundle.getString(VuforiaPlugin.PLUGIN_ACTION) : null;

            if(receivedAction != null) {
                switch (receivedAction) {
                    case VuforiaPlugin.DISMISS_ACTION:
                        //Vuforia.deinit();
                        finish();
                        break;
                    case VuforiaPlugin.PAUSE_ACTION:
                        doStopTrackers();
                        break;
                    case VuforiaPlugin.RESUME_ACTION:
                        doStartTrackers();
                        break;
                    case VuforiaPlugin.UPDATE_TARGETS_ACTION:
                        String targets = intent.getStringExtra("ACTION_DATA");
                        doUpdateTargets(targets);
                        break;
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);

        //Remove title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        //Remove notification bar
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);


        //Grab a reference to our Intent so that we can get the extra data passed into it
        Intent intent = getIntent();

        //Get the vuoria license key that was passed into the plugin
        mLicenseKey = intent.getStringExtra("LICENSE_KEY");
//"AfwNugr/////AAABmXSkhi4Wc0y2k2u/t+KF1/iJ4ZMm1p1k8duNetuGt2xMVstBzN2aOC3aNkUMWuCQjUcdoluNVL+wkRqiden+ZsuveS8ccvkbGFZyPLexUsFBZrlrycv4c+O+tH6stLswQ8oh9mpwqFj09Kajfgr8Mabf40Y+QjtGffxa/Un93OMnULUCebsQVJVlY18GsUydNSSc5ijLmKqQpTLFp5xDWnSsVD3Pz9gE5z7Bvyv+2oI35uccwY/gEsKQhHs4oCbgESgTqMyTxvICvQO4vYEljmt3Ac4g4CQjVZcttQiAiRLxTDFcfY0xxORaXc9CltcVq4TWrviKRKAZsqDMLz2eOepHdHI42gpCfIJHDGnfMpTF"

        vuforiaAppSession = new SampleApplicationSession(this, mLicenseKey);

        //Get the passed in targets file
        String target_file = intent.getStringExtra("IMAGE_TARGET_FILE");
        mTargets = intent.getStringExtra("IMAGE_TARGETS");
        mOverlayMessage = intent.getStringExtra("OVERLAY_TEXT");
        mDisplayCloseButton = intent.getBooleanExtra("DISPLAY_CLOSE_BUTTON", true);
        mDisplayDevicesIcon = intent.getBooleanExtra("DISPLAY_DEVICES_ICON", true);
        mAutostopOnImageFound = intent.getBooleanExtra("STOP_AFTER_IMAGE_FOUND", true);

        Log.d(LOGTAG, "PARAM :: VUFORIA RECEIVED FILE: " + target_file);
        Log.d(LOGTAG, "PARAM :: VUTORIA TARGETS: " + mTargets);
        Log.d(LOGTAG, "PARAM :: OVERLAY MESSAGE: " + mOverlayMessage);
        Log.d(LOGTAG, "PARAM :: DISPLAY_CLOSE_BUTTON: " + mDisplayCloseButton);
        Log.d(LOGTAG, "PARAM :: DISPLAY_DEVICES_ICON: " + mDisplayDevicesIcon);
        Log.d(LOGTAG, "PARAM :: STOP_AFTER_IMAGE_FOUND: " + mAutostopOnImageFound);

        startLoadingAnimation();

        mDatasetStrings.add(target_file);

        vuforiaAppSession
            .initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        mGestureDetector = new GestureDetector(getApplicationContext(), new GestureListener());
        
        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith("droid");

    }


    private class GestureListener extends
        GestureDetector.SimpleOnGestureListener
    {

        @Override
        public boolean onDown(MotionEvent e)
        {
            return true;
        }


        // Process Single Tap event to trigger autofocus
        @Override
        public boolean onSingleTapUp(MotionEvent e)
        {
            boolean result = CameraDevice.getInstance().setFocusMode(
                    CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO);
            if (!result)
                Log.e("SingleTapUp", "Unable to trigger focus");

            return true;
        }
    }

    @Override
    protected void onStart()
    {
        //register broadcast receiver
        if (vuforiaActionReceiver == null) {
            vuforiaActionReceiver = new ActionReceiver();
        }

        IntentFilter intentFilter = new IntentFilter(VuforiaPlugin.PLUGIN_ACTION);
        registerReceiver(vuforiaActionReceiver, intentFilter);

        Log.d(LOGTAG, "onStart");
        super.onStart();
    }

    @Override
    protected void onStop()
    {
        //un-register broadcasts receiver
        if (vuforiaActionReceiver != null) {
            unregisterReceiver(vuforiaActionReceiver);
        }
        Log.d(LOGTAG, "onStop");
        super.onStop();

    }

    @Override
    protected void onResume()
    {
        Log.d(LOGTAG, "onResume");
        super.onResume();

        showProgressIndicator(true);
        
        // This is needed for some Droid devices to force portrait
        if (mIsDroidDevice)
        {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        vuforiaAppSession.onResume();
    }


    // Called whenever the device orientation or screen resolution changes
    @Override
    public void onConfigurationChanged(Configuration config)
    {
        Log.d(LOGTAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);
        
        vuforiaAppSession.onConfigurationChanged();
    }


    @Override
    protected void onPause()
    {
        Log.d(LOGTAG, "onPause");
        super.onPause();
        
        if (mGlView != null)
        {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }
        
        vuforiaAppSession.onPause();
    }
    

    @Override
    protected void onDestroy()
    {
        Log.d(LOGTAG, "onDestroy");
        super.onDestroy();
        
        try
        {
            vuforiaAppSession.stopAR();
        } catch (SampleApplicationException e)
        {
            Log.e(LOGTAG, e.getString());
        }
        
        System.gc();
    }
    

    private void initApplicationAR()
    {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();
        
        mGlView = new SampleApplicationGLView(getApplicationContext());
        mGlView.init(translucent, depthSize, stencilSize);

        mRenderer = new ImageTargetRenderer(this, vuforiaAppSession, mTargets);
        mGlView.setRenderer(mRenderer);
        mGlView.setPreserveEGLContextOnPause(true);

        setRendererReference(mRenderer);
    }
    
    
    private void startLoadingAnimation()
    {
        // Get the project's package name and a reference to it's resources
        String package_name = getApplication().getPackageName();
        Resources resources = getApplication().getResources();

        mUILayout = (RelativeLayout) View.inflate(getApplicationContext(), resources.getIdentifier("camera_overlay", "layout", package_name), null);
        
        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);

        // Gets a reference to the loading dialog
        loadingDialogHandler.mLoadingDialogContainer = mUILayout
            .findViewById(resources.getIdentifier("loading_indicator", "id", package_name));
        
        // Shows the loading indicator at start
        loadingDialogHandler
            .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);

        TextView overlayTextView = mUILayout.findViewById(resources.getIdentifier("overlay_message_top", "id", package_name));

        Log.d(LOGTAG, "Overlay Text: " + mOverlayMessage);

        // Hide the close button if needed
        Button closeButton = mUILayout.findViewById(resources.getIdentifier("overlay_message_top", "close_button_top", package_name));
        if(!mDisplayCloseButton)
            closeButton.setVisibility(View.GONE);

        ImageView devicesIconImage = mUILayout.findViewById(resources.getIdentifier("devices_icon_top", "id", package_name));

        if(!mDisplayDevicesIcon)
            devicesIconImage.setVisibility(View.GONE);
        // Updates the overlay message with the text passed-in
        overlayTextView.setText(mOverlayMessage);

        // If the message doesn't exist/is empty, set the black overlay container to be nearly transparent.
        LinearLayout overlayContainer = mUILayout.findViewById(resources.getIdentifier("layout_top", "id", package_name));
        if(overlayTextView.getText().equals("")) {
            overlayContainer.setBackgroundColor(Color.parseColor("#00000000"));
        }

        // Adds the inflated layout to the view
        addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT));
        
    }
    

    @Override
    public boolean doLoadTrackersData()
    {
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
            .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
        {
            return false;
        }
        
        if (mCurrentDataset == null)
        {
            mCurrentDataset = objectTracker.createDataSet();
        }
        
        if (mCurrentDataset == null)
        {
            return false;
        }

        //Determine the storage type.
        int storage_type;
        int mCurrentDatasetSelectionIndex = 0;

        String dataFile = mDatasetStrings.get(mCurrentDatasetSelectionIndex);

        if(dataFile.startsWith(FILE_PROTOCOL)){
            storage_type = STORAGE_TYPE.STORAGE_ABSOLUTE;
            dataFile = dataFile.substring(FILE_PROTOCOL.length(), dataFile.length());
            mDatasetStrings.set(mCurrentDatasetSelectionIndex, dataFile);
            Log.d(LOGTAG, "Reading the absolute path: " + dataFile);
        }else{
            storage_type = STORAGE_TYPE.STORAGE_APPRESOURCE;
            Log.d(LOGTAG, "Reading the path " + dataFile + " from the assets folder.");
        }

        if (!mCurrentDataset.load(
                mDatasetStrings.get(mCurrentDatasetSelectionIndex), storage_type)) {
            return false;
        }
        
        if (!objectTracker.activateDataSet(mCurrentDataset))
        {
            return false;
        }
        
        TrackableList trackableList = mCurrentDataset.getTrackables();
        for (Trackable trackable : trackableList)
        {
            String name = "Current Dataset : " + trackable.getName();
            trackable.setUserData(name);
            Log.d(LOGTAG, "UserData:Set the following user data "
                + trackable.getUserData());
        }
        
        return true;
    }
    
    
    @Override
    public boolean doUnloadTrackersData()
    {
        // Indicate if the trackers were unloaded correctly
        boolean result = true;
        
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
            .getTracker(ObjectTracker.getClassType());

        if (objectTracker == null)
        {
            return false;
        }
        
        if (mCurrentDataset != null && mCurrentDataset.isActive())
        {
            if (objectTracker.getActiveDataSets().at(0).equals(mCurrentDataset)
                && !objectTracker.deactivateDataSet(mCurrentDataset))
            {
                result = false;
            }
            else if (!objectTracker.destroyDataSet(mCurrentDataset))
            {
                result = false;
            }
            
            mCurrentDataset = null;
        }
        
        return result;
    }


    @Override
    public void onVuforiaResumed()
    {
        if (mGlView != null)
        {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }
    }


    @Override
    public void onVuforiaStarted()
    {
        mRenderer.updateRenderingPrimitives();

        //trigger continuous auto focus
        if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO))
        {
            // If continuous autofocus mode fails, attempt to set to a different mode
            if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO))
            {
                CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);
            }

        }

        showProgressIndicator(false);
    }


    private void showProgressIndicator(boolean show)
    {
        if (show)
        {
            loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);
        }
        else
        {
            loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
        }
    }


    // Called once Vuforia has been initialized or
    // an error has caused Vuforia initialization to stop
    @Override
    public void onInitARDone(SampleApplicationException exception)
    {
        if (exception == null)
        {
            initApplicationAR();
            
            mRenderer.setActive(true);
            
            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
            
            // Sets the UILayout to be drawn in front of the camera
            mUILayout.bringToFront();

            mUILayout.setBackgroundColor(Color.TRANSPARENT);

            vuforiaAppSession.startAR();
        }
        else
        {
            Log.e(LOGTAG, exception.getString());
            showInitializationErrorMessage(exception.getString());
        }
    }
    

    private void showInitializationErrorMessage(String message)
    {
        final String errorMessage = message;
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                if (mErrorDialog != null)
                {
                    mErrorDialog.dismiss();
                }

                String package_name = getApplication().getPackageName();
                Resources resources = getApplication().getResources();

                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        ImageTargets.this);
                builder
                    .setMessage(errorMessage)
                    .setTitle("Error")
                    .setCancelable(false)
                    .setIcon(0)
                    .setPositiveButton(getString(resources.getIdentifier("button_OK", "string", package_name)),
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int id)
                            {
                                finish();
                            }
                        });
                
                mErrorDialog = builder.create();
                mErrorDialog.show();
            }
        });
    }


    // Called every frame
    @Override
    public void onVuforiaUpdate(State state)
    {
    }
    
    
    @Override
    public boolean doInitTrackers()
    {
        // Indicate if the trackers were initialized correctly
        boolean result = true;
         
        TrackerManager tManager = TrackerManager.getInstance();

        Tracker tracker = tManager.initTracker(ObjectTracker.getClassType());
        if (tracker == null)
        {
            Log.e(
                LOGTAG,
                "Tracker not initialized. Tracker already initialized or the camera is already started");
            result = false;
        } else
        {
            Log.i(LOGTAG, "Tracker successfully initialized");
        }

        return result;
    }
    
    
    @Override
    public boolean doStartTrackers()
    {
        // Indicate if the trackers were started correctly
        boolean result = true;

        TrackerManager trackerManager = TrackerManager.getInstance();

        Tracker objectTracker = trackerManager.getTracker(ObjectTracker.getClassType());

        if (objectTracker != null && objectTracker.start())
        {
            Log.i(LOGTAG, "Successfully started Object Tracker");
        }
        else
        {
            Log.e(LOGTAG, "Failed to start Object Tracker");
            result = false;
        }

        return result;
    }
    
    
    @Override
    public boolean doStopTrackers()
    {
        // Indicate if the trackers were stopped correctly
        boolean result = true;

        TrackerManager trackerManager = TrackerManager.getInstance();

        Tracker objectTracker = trackerManager.getTracker(ObjectTracker.getClassType());
        if (objectTracker != null)
        {
            objectTracker.stop();
            Log.i(LOGTAG, "Successfully stopped object tracker");
        }
        else
        {
            Log.e(LOGTAG, "Failed to stop object tracker");
            result = false;
        }

        return result;
    }
    
    
    @Override
    public boolean doDeinitTrackers()
    {
        TrackerManager tManager = TrackerManager.getInstance();

        // Indicate if the trackers were deinitialized correctly
        return tManager.deinitTracker(ObjectTracker.getClassType());
    }
    

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        // Process the Gestures
        return mGestureDetector.onTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        Log.d(LOGTAG, "back button pressed... closing activity");

        Intent mIntent = new Intent();
        mIntent.putExtra("name", "CLOSED");
        setResult(VuforiaPlugin.MANUAL_CLOSE_RESULT, mIntent);
        //Vuforia.deinit();
        super.onBackPressed();
    }

    public void handleCloseButton(View view){
        onBackPressed();
    }

    public void imageFound(String imageName) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("name", imageName);

        setResult(VuforiaPlugin.IMAGE_REC_RESULT, resultIntent);

        doStopTrackers();

        Log.d(LOGTAG, "mAuto Stop On Image Found: " + mAutostopOnImageFound);

        if(mAutostopOnImageFound) {
            //Vuforia.deinit();

            finish();
        } else {
            Log.d(LOGTAG, "Sending repeat callback");

            VuforiaPlugin.sendImageFoundUpdate(imageName);
        }
    }

    public void doUpdateTargets(String targets) {
        mTargets = targets;

        mRenderer.updateTargetStrings(mTargets);
    }
}
