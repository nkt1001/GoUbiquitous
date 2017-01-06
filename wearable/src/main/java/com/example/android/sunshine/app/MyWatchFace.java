/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MyWatchFace extends CanvasWatchFaceService {
    private static final String TAG = MyWatchFace.class.getSimpleName();
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = 500;

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener{
        final Handler mUpdateTimeHandler = new EngineHandler(this);

        private static final String WEARABLE_DATA_PATH = "/wearable_data";

//        private boolean mWeatherDataUpdated=false;

        private static final String COLON_STRING = ":";
        private boolean mRegisteredTimeZoneReceiver = false;


        private Paint mBackgroundPaint;
        private Paint mWeatherIconPaint;
        private Paint mHighTempPaint;
        private Paint mDatePaint;
        private Paint mHourPaint;
        private Paint mMinutePaint;
        private Paint mColonPaint;
        private Paint mDividerPaint;

        private float mColonWidth;
        private Calendar mCalendar;
        private SimpleDateFormat mDayOfWeekFormat;
        private Date mDate;

        private float mYOffset;
        private float mXOffset;
        private float mCenterX;
        private float mCenterY;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;
        private boolean mAmbient;
        private boolean mBurnInProtection;
        private boolean mRound;
        private boolean mShouldDrawColons;

        private int mIBackgroundColor ;
        private int mABackgroundColor ;
        private int mITextColor ;
        private int mIColonColor ;

        private Bitmap mWeatherIconBitmap;
        private Bitmap mGrayWeatherIconBitmap;
        private String mHighTemp;
        private String mLowTemp;


        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();


        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
                invalidate();
            }
        };


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = MyWatchFace.this.getResources();

            mIBackgroundColor =resources.getColor(R.color.background) ;
            mABackgroundColor =resources.getColor(R.color.ambient_background) ;
            mITextColor =resources.getColor(R.color.text_color) ;
            mIColonColor =resources.getColor(R.color.text_color_colon) ;

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mIBackgroundColor);

            mWeatherIconPaint = new Paint();

            mDatePaint = createTextPaint(mITextColor, NORMAL_TYPEFACE);
            mDatePaint.setAlpha(180);

            mHighTempPaint = createTextPaint(mITextColor, BOLD_TYPEFACE);

            mHourPaint = createTextPaint(mITextColor, BOLD_TYPEFACE);
            mMinutePaint = createTextPaint(mITextColor, BOLD_TYPEFACE);
            mColonPaint = createTextPaint(mIColonColor, NORMAL_TYPEFACE);

            mDividerPaint = new Paint();
            mDividerPaint.setColor(resources.getColor(R.color.text_color));
            mDividerPaint.setStrokeWidth(2f);
            mDividerPaint.setAlpha(80);

            mCalendar = Calendar.getInstance();
            mDate = new Date();

            initWeatherDetails(0,0,"clear");
            initFormats();
        }

        private void initWeatherDetails(int high, int low, String icon  ){
            if(icon == null){
                icon = "clear";
            }
            int resID = getResources().getIdentifier("ic_" + icon , "drawable", getPackageName());
            int resIDBW = getResources().getIdentifier("ic_" + icon + "_bw" , "drawable", getPackageName());

            mWeatherIconBitmap = BitmapFactory.decodeResource(getResources(), resID);
            mGrayWeatherIconBitmap= BitmapFactory.decodeResource(getResources(), resIDBW);
            mHighTemp = String.format("%3s",String.valueOf(high)) + "° C";
            mLowTemp = String.format("%3s",String.valueOf(low)) + "° C";
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            Log.d(TAG,"Visibility changed");
            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();
                //registerWeatherReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
            } else {
                unregisterReceiver();
                //unregisterWeatherReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void initFormats() {
            mDayOfWeekFormat = new SimpleDateFormat("EEE, dd MMMM yyyy", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }


        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            mRound = insets.isRound();
            float textSize = resources.getDimension(mRound
                    ? R.dimen.text_size_round : R.dimen.text_size);
            float dateSize = resources.getDimension(mRound
                    ? R.dimen.date_size_round : R.dimen.date_size);
            float highTempSize = resources.getDimension(mRound
                    ? R.dimen.high_temp_size_round : R.dimen.high_temp_size);

            mYOffset = resources.getDimension(mRound
                    ? R.dimen.y_offset_round : R.dimen.y_offset);

            mDatePaint.setTextSize(dateSize);
            mHighTempPaint.setTextSize(highTempSize);
            mHourPaint.setTextSize(textSize);
            mMinutePaint.setTextSize(textSize);
            mColonPaint.setTextSize(textSize);

            mColonWidth = mColonPaint.measureText(COLON_STRING);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            mHourPaint.setTypeface(mBurnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);
            mMinutePaint.setTypeface(mBurnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    boolean antiAlias = !inAmbientMode;
                    mDatePaint.setAntiAlias(antiAlias);
                    mHourPaint.setAntiAlias(antiAlias);
                    mMinutePaint.setAntiAlias(antiAlias);
                    mColonPaint.setAntiAlias(antiAlias);
                }
                if(mAmbient){
                    mBackgroundPaint.setColor(mABackgroundColor);
                    mColonPaint.setColor(mITextColor);
                    mHourPaint.setTypeface(NORMAL_TYPEFACE);
                    mMinutePaint.setTypeface(NORMAL_TYPEFACE);
                } else{
                    mBackgroundPaint.setColor(mIBackgroundColor);
                    mColonPaint.setColor(mIColonColor);
                    mHourPaint.setTypeface(mBurnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);
                    mMinutePaint.setTypeface(mBurnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {

            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (mAmbient) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawColor(mIBackgroundColor);
            }


            if (mRound) {
                canvas.drawCircle(mCenterX, mCenterY, mCenterX, mBackgroundPaint);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }


            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            mShouldDrawColons = (System.currentTimeMillis() % 1000) < 500;


            String hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));

            float hourTextLength = mHourPaint.measureText(hourString);
            float minuteTextLength = mMinutePaint.measureText(minuteString);


            mXOffset = (bounds.width() - (hourTextLength + mColonWidth + minuteTextLength)) / 2;
            float x = mXOffset;

            canvas.drawText(hourString, x, mYOffset, mHourPaint);
            x += mHourPaint.measureText(hourString);


            if (mAmbient || mShouldDrawColons) {
                canvas.drawText(COLON_STRING, x, mYOffset - 5, mColonPaint);
            }
            x += mColonWidth;


            canvas.drawText(minuteString, x, mYOffset, mHourPaint);

            float y = getTextHeight(hourString, mHourPaint) + mYOffset - 20;
            String dayString = mDayOfWeekFormat.format(mDate);
            x = (bounds.width() - mDatePaint.measureText(dayString)) / 2;

            canvas.drawText(dayString, x, y, mDatePaint);

            y += getTextHeight(dayString, mDatePaint) ;
            if (!mAmbient) canvas.drawLine(mCenterX - 32f, y + 8, mCenterX + 32f, y + 8, mDividerPaint);

            int drawWeather = 0;
            if (mAmbient) {
                if(!mLowBitAmbient && !mBurnInProtection){
                    drawWeather = 2;
                }
            } else {
                drawWeather = 1;
            }

            if(drawWeather > 0){
                y += getTextHeight(dayString, mDatePaint);
                x = (bounds.width() - (mWeatherIconBitmap.getWidth() + mDatePaint.measureText(mHighTemp) + 16 + mDatePaint.measureText(mLowTemp) + 16)) /2;
                if(drawWeather == 1){
                    canvas.drawBitmap(mWeatherIconBitmap, x, y, mWeatherIconPaint);
                } else{
                    canvas.drawBitmap(mGrayWeatherIconBitmap, x, y, mWeatherIconPaint);
                }
                x += mWeatherIconBitmap.getWidth() + 5;
                y = y + mWeatherIconBitmap.getHeight() / 2 + getTextHeight(mHighTemp, mHighTempPaint) / 2;
                canvas.drawText(mHighTemp, x, y, mHighTempPaint);
                x += mHighTempPaint.measureText(mHighTemp) + 5;
                canvas.drawText(mLowTemp, x, y, mDatePaint);
            }

        }

        /**
         * @return text height
         */
        private float getTextHeight(String text, Paint paint) {

            Rect rect = new Rect();
            paint.getTextBounds(text, 0, text.length(), rect);
            return rect.height();
        }

        private String formatTwoDigitNumber(int hour) {
            return String.format(Locale.getDefault(), "%02d", hour);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override // DataApi.DataListener
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.d(TAG, "onDataChanged");
            for (DataEvent dataEvent : dataEvents) {
                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    continue;
                }

                DataItem dataItem = dataEvent.getDataItem();
                if (!dataItem.getUri().getPath().equals(
                        WEARABLE_DATA_PATH)) {
                    continue;
                }

                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap dMap = dataMapItem.getDataMap();
                int high = (int) Math.round(dMap.getDouble("high"));
                int low = (int) Math.round(dMap.getDouble("low"));
                Long id = dMap.getLong("id");
                String icon = Utility.getArtUrlForWeatherCondition(id);
                initWeatherDetails(high, low, icon);
                invalidate();

            }
//            mWeatherDataUpdated = true;
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionSuspended: " + cause);
            }
        }

        @Override
        public void onConnected(Bundle connectionHint) {

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnected: " + connectionHint);
            }
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);

        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionFailed: " + result);
            }
        }

//        class SendToDataLayerThread extends Thread {
//            String path;
//            DataMap dataMap;
//
//            // Constructor for sending data objects to the data layer
//            SendToDataLayerThread(String p, DataMap data) {
//                path = p;
//                dataMap = data;
//            }
//
//            public void run() {
//                // Construct a DataRequest and send over the data layer
//                PutDataMapRequest putDMR = PutDataMapRequest.create(path);
//                putDMR.getDataMap().putAll(dataMap);
//                PutDataRequest request = putDMR.asPutDataRequest();
//                DataApi.DataItemResult result = Wearable.DataApi.putDataItem(mGoogleApiClient, request).await();
//                if (result.getStatus().isSuccess()) {
//                    Log.d(TAG, "DataMap: " + dataMap + " sent successfully to data layer ");
//                } else {
//                    // Log an error
//                    Log.d(TAG, "ERROR: failed to send DataMap to data layer");
//                }
//            }
//        }
//
    }
}
