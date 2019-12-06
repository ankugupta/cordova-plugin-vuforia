/*===============================================================================
Copyright (c) 2019 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.mattrayner.vuforia.app;

import android.opengl.GLES20;
import android.util.Log;

import com.vuforia.State;
import com.vuforia.Trackable;
import com.vuforia.TrackableResult;
import com.vuforia.TrackableResultList;
import com.vuforia.Vuforia;
import com.mattrayner.vuforia.app.SampleAppRenderer;
import com.mattrayner.vuforia.app.SampleAppRendererControl;
import com.mattrayner.vuforia.app.SampleApplicationSession;
import com.mattrayner.vuforia.app.SampleRendererBase;
import com.mattrayner.vuforia.app.utils.LoadingDialogHandler;

import java.lang.ref.WeakReference;


/**
 * The renderer class for the Image Targets sample.
 *
 * In the renderFrame() function you can render augmentations to display over the Target
 */
public class ImageTargetRenderer extends SampleRendererBase implements SampleAppRendererControl
{
    private static final String LOGTAG = "ImageTargetRenderer";
    
    private final WeakReference<ImageTargets> mActivityRef;
    
    private String mTargets;
    
    ImageTargetRenderer(ImageTargets activity, SampleApplicationSession session, String targets)
    {
        mActivityRef = new WeakReference<>(activity);
        vuforiaAppSession = session;
        mTargets = targets;

        // SampleAppRenderer used to encapsulate the use of RenderingPrimitives setting
        // the device mode AR/VR and stereo mode
        mSampleAppRenderer = new SampleAppRenderer(this, mActivityRef.get(), vuforiaAppSession.getVideoMode(),
                0.01f , 5f);
    }


    public void updateRenderingPrimitives()
    {
        mSampleAppRenderer.updateRenderingPrimitives();
    }


    public void setActive(boolean active)
    {
        mSampleAppRenderer.setActive(active);
    }


    // The render function.
    // This function is called from the SampleAppRenderer by using the RenderingPrimitives views.
    // The state is owned by SampleAppRenderer which is controlling its lifecycle.
    // NOTE: State should not be cached outside this method.
    public void renderFrame(State state, float[] projectionMatrix)
    {
        // Renders video background replacing Renderer.DrawVideoBackground()
        mSampleAppRenderer.renderVideoBackground();

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glFrontFace(GLES20.GL_CCW);   // Back camera

        TrackableResultList trackableResultList = state.getTrackableResults();

        // Iterate through trackable results and render any augmentations
        for (TrackableResult result : trackableResultList)
        {
            Trackable trackable = result.getTrackable();
            String obj_name = trackable.getName();

            Log.d(LOGTAG, "TARGET :: Found: " + obj_name);

            /*
             * Our targets array has been flattened to a string so will equal something like: ["one", "two"]
             * So, to stop weak matches such as 'two' within ["onetwothree", "two"] we wrap the term in
             * speech marks such as '"two"'
             */
            boolean looking_for = mTargets.toLowerCase().contains("\"" + obj_name.toLowerCase() + "\"");

            if (looking_for)
            {
                mActivityRef.get().imageFound(obj_name);
            }
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    }

    @Override
    public void initRendering()
    {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
                : 1.0f);

        // Hide the Loading Dialog
        mActivityRef.get().loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
    }

    public void updateTargetStrings(String targets) {
        mTargets = targets;
    }
}
