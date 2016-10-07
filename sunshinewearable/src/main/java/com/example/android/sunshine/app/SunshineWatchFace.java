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
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    private int weatherId;


    /**
     * Update rate in milliseconds for interactive mode - once every 10s
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(10);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mMinuteTextPaint;
        Paint mHourTextPaint;
        Paint mDateTextPaint;
        Paint mLowTextPaint;
        Paint mHighTextPaint;
        SimpleDateFormat mDateFormat;
        boolean mAmbient;
        Calendar mCalendar;
        Date mDate;
        Bitmap mWeatherIcon;
        float mLineHeight;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mLineHeight = resources.getDimension(R.dimen.digital_line_height);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mMinuteTextPaint = new Paint();
            mMinuteTextPaint = createTextPaint(resources.getColor(R.color.digital_text), NORMAL_TYPEFACE);

            mHourTextPaint = new Paint();
            mHourTextPaint = createTextPaint(resources.getColor(R.color.digital_text), BOLD_TYPEFACE);

            mLowTextPaint = new Paint();
            mLowTextPaint = createTextPaint(resources.getColor(R.color.digital_text_secondary), NORMAL_TYPEFACE);
            mLowTextPaint.setTextSize(resources.getDimension(R.dimen.digital_temp_text_size));

            mHighTextPaint = new Paint();
            mHighTextPaint = createTextPaint(resources.getColor(R.color.digital_text), BOLD_TYPEFACE);
            mHighTextPaint.setTextSize(resources.getDimension(R.dimen.digital_temp_text_size));

            mDateTextPaint = new Paint();
            mDateTextPaint = createTextPaint(resources.getColor(R.color.digital_text_secondary), NORMAL_TYPEFACE);
            mDateTextPaint.setTextSize(resources.getDimension(R.dimen.digital_date_text_size));

            mCalendar = Calendar.getInstance();

            //get icon
            Drawable weatherBitmap = resources.getDrawable(R.drawable.ic_clear,null);
            mWeatherIcon = ((BitmapDrawable) weatherBitmap).getBitmap();


            //set Date formatter
            mDateFormat = new SimpleDateFormat("EEE, MMM d yyyy");
            mDate = new Date();
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

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mMinuteTextPaint.setTextSize(textSize);
            mHourTextPaint.setTextSize(textSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
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
                    mMinuteTextPaint.setAntiAlias(!inAmbientMode);
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
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            float xTimeWidth;

            String hourString = String.format("%02d:", mCalendar.get(Calendar.HOUR));
            String minuteString = String.format("%02d", mCalendar.get(Calendar.MINUTE));

            //Calculate width of full time string
            xTimeWidth = mHourTextPaint.measureText(hourString) + mMinuteTextPaint.measureText(minuteString);

            //paint time
            canvas.drawText(hourString, bounds.centerX() - (xTimeWidth / 2), mYOffset, mHourTextPaint);
            canvas.drawText(minuteString, bounds.centerX() - ((xTimeWidth / 2) - mHourTextPaint.measureText(hourString)), mYOffset, mMinuteTextPaint);

            //Calculate width of date string
            float xDateWidth = mDateTextPaint.measureText(mDateFormat.format(mDate).toUpperCase());

            //now paint date string
            canvas.drawText(mDateFormat.format(mDate).toUpperCase(), bounds.centerX() - (xDateWidth / 2), mYOffset + mLineHeight * 2, mDateTextPaint);

            //now show weather if not in ambient mode

            if (!isInAmbientMode()) {

                //get weather data from shared preferences
                SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(getString(R.string.weather_data_prefs), Context.MODE_PRIVATE);

                String highTemp = sharedPref.getString(getString(R.string.high_temp), "-");
                String lowTemp = sharedPref.getString(getString(R.string.low_temp), "-");

                String highTempString = String.format(SunshineWatchFace.this.getString(R.string.format_temperature), highTemp);
                String lowTempString = String.format(SunshineWatchFace.this.getString(R.string.format_temperature), lowTemp);

            /*
             * Calculate size of icon bitmap so it is 1/4th of a screen width
             */
                float iconSize = (float) (bounds.width() / 4);

            /* Scale loaded background image (more efficient) if surface dimensions change. */
                float scale = ((float) iconSize) / (float) mWeatherIcon.getWidth();

                mWeatherIcon = Bitmap.createScaledBitmap(mWeatherIcon,
                        (int) (mWeatherIcon.getWidth() * scale),
                        (int) (mWeatherIcon.getHeight() * scale), true);


                //get center point for lower row items
                float posCenterLeftThird = ((bounds.width() / 3)) / 1.5f - (mWeatherIcon.getWidth() / 2); //minus half width of scaled bitmpa
                float posCenter = bounds.centerX() - (mHighTextPaint.measureText(highTempString) / 2);
                float posCenterRightThird = posCenter * 1.6f;


                canvas.drawBitmap(mWeatherIcon, posCenterLeftThird, (mYOffset + mLineHeight * 6) - (mWeatherIcon.getHeight() / 1.5f), null);
                canvas.drawText(highTempString, posCenter, mYOffset + mLineHeight * 6, mHighTextPaint);
                canvas.drawText(lowTempString, posCenterRightThird, mYOffset + mLineHeight * 6, mLowTextPaint);

            }

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
    }
}
