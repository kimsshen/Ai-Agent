package com.example.industrialai.iiot;

public class InvalidDeviceIdException extends RuntimeException {

    public InvalidDeviceIdException(String deviceId) {
        super("Invalid device id: " + deviceId);
    }
}
