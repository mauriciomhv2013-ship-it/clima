package com.smsflow.app;

import android.app.AppComponentFactory;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;

import java.lang.reflect.Constructor;

public class SmsFlowAppComponentFactory extends AppComponentFactory {
    @Override
    public Service instantiateService(ClassLoader cl, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        try {
            Class<?> type = cl.loadClass(className);
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (Service) constructor.newInstance();
        } catch (ClassNotFoundException e) {
            throw e;
        } catch (Exception e) {
            InstantiationException wrapped = new InstantiationException("Cannot instantiate service " + className);
            wrapped.initCause(e);
            throw wrapped;
        }
    }

    @Override
    public BroadcastReceiver instantiateReceiver(ClassLoader cl, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        try {
            Class<?> type = cl.loadClass(className);
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (BroadcastReceiver) constructor.newInstance();
        } catch (ClassNotFoundException e) {
            throw e;
        } catch (Exception e) {
            InstantiationException wrapped = new InstantiationException("Cannot instantiate receiver " + className);
            wrapped.initCause(e);
            throw wrapped;
        }
    }
}
