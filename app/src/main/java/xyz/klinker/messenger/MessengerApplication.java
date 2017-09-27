/*
 * Copyright (C) 2017 Luke Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.klinker.messenger;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.support.text.emoji.EmojiCompat;
import android.support.text.emoji.FontRequestEmojiCompatConfig;
import android.support.v4.os.BuildCompat;
import android.support.v4.provider.FontRequest;
import android.support.v7.app.AppCompatDelegate;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.List;

import xyz.klinker.messenger.api.implementation.Account;
import xyz.klinker.messenger.api.implementation.ApiUtils;
import xyz.klinker.messenger.api.implementation.firebase.FirebaseApplication;
import xyz.klinker.messenger.api.implementation.firebase.FirebaseMessageHandler;
import xyz.klinker.messenger.api.implementation.firebase.MessengerFirebaseMessagingService;
import xyz.klinker.messenger.shared.data.DataSource;
import xyz.klinker.messenger.shared.data.Settings;
import xyz.klinker.messenger.shared.data.model.Conversation;
import xyz.klinker.messenger.shared.data.pojo.BaseTheme;
import xyz.klinker.messenger.shared.service.CreateNotificationChannelService;
import xyz.klinker.messenger.shared.service.FirebaseHandlerService;
import xyz.klinker.messenger.shared.service.FirebaseResetService;
import xyz.klinker.messenger.shared.util.AndroidVersionUtil;
import xyz.klinker.messenger.shared.util.DynamicShortcutUtils;
import xyz.klinker.messenger.shared.util.EmojiInitializer;
import xyz.klinker.messenger.shared.util.TimeUtils;

import static xyz.klinker.messenger.api.implementation.firebase.MessengerFirebaseMessagingService.EXTRA_DATA;
import static xyz.klinker.messenger.api.implementation.firebase.MessengerFirebaseMessagingService.EXTRA_OPERATION;

/**
 * Base application that will serve as any intro for any context in the rest of the app. Main
 * function is to enable night mode so that colors change depending on time of day.
 */
public class MessengerApplication extends FirebaseApplication {

    /**
     * Enable night mode and set it to auto. It will switch depending on time of day.
     */
    static {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            ApiUtils.INSTANCE.setEnvironment(getString(R.string.environment));
        } catch (Exception e) {
            ApiUtils.INSTANCE.setEnvironment("release");
        }

        enableSecurity();
        Account.INSTANCE.init(this);
        EmojiInitializer.INSTANCE.initializeEmojiCompat(getApplicationContext());

        BaseTheme theme = Settings.get(this).baseTheme;
        if (theme == BaseTheme.ALWAYS_LIGHT) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (theme.isDark || TimeUtils.isNight()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }

        if (AndroidVersionUtil.isAndroidO()) {
            startForegroundService(new Intent(this, CreateNotificationChannelService.class));
        }
    }

    public void refreshDynamicShortcuts() {
        if (!"robolectric".equals(Build.FINGERPRINT) && BuildCompat.isAtLeastNMR1() && !Settings.get(this).firstStart) {
            new Thread(() -> {
                try {
                    Thread.sleep(10 * TimeUtils.SECOND);
                    DataSource source = DataSource.INSTANCE;

                    List<Conversation> conversations = source.getPinnedConversationsAsList(this);
                    if (conversations.size() == 0) {
                        conversations = source.getUnarchivedConversationsAsList(this);
                    }

                    new DynamicShortcutUtils(MessengerApplication.this).buildDynamicShortcuts(conversations);
                } catch (Exception e) { }
            }).start();
        }
    }

    /**
     * By default, java does not allow for strong security schemes due to export laws in other
     * countries. This gets around that. Might not be necessary on Android, but we'll put it here
     * anyways just in case.
     */
    private static void enableSecurity() {
        try {
            Field field = Class.forName("javax.crypto.JceSecurity").
                    getDeclaredField("isRestricted");
            field.setAccessible(true);
            field.set(null, Boolean.FALSE);
        } catch (Exception e) {

        }
    }

    @Override
    public FirebaseMessageHandler getFirebaseMessageHandler() {
        return new FirebaseMessageHandler() {
            @Override
            public void handleMessage(Application application, String operation, String data) {
                new Thread(() -> FirebaseHandlerService.process(application, operation, data)).start();
            }

            @Override
            public void handleDelete(Application application) {
                final Intent handleMessage = new Intent(application, FirebaseResetService.class);
                if (AndroidVersionUtil.isAndroidO()) {
                    startForegroundService(handleMessage);
                } else {
                    startService(handleMessage);
                }
            }
        };
    }
}
