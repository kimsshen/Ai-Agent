package com.example.industrialai.iiot;

public class DeviceNotFoundException extends RuntimeException {

    public DeviceNotFoundException(String deviceId) {
        super("Device not found: " + deviceId);
    }
}
