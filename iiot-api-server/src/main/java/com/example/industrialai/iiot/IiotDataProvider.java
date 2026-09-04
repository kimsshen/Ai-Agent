package com.example.industrialai.iiot;

import com.example.industrialai.model.Alarm;
import com.example.industrialai.model.Device;
import com.example.industrialai.model.Telemetry;
import com.example.industrialai.model.WorkOrder;

import java.util.List;

/**
 * Business API data boundary. Implementations may read from SCADA/IoT,
 * a time-series database, an alarm center, or CMMS/EAM.
 */
public interface IiotDataProvider {

    List<Device> findAllDevices();

    Device findDevice(String deviceId);

    Telemetry findLatestTelemetry(String deviceId);

    List<Telemetry> findTelemetryHistory(String deviceId, int limit);

    List<Alarm> findAlarms(String deviceId, boolean activeOnly);

    List<WorkOrder> findWorkOrders(String deviceId);
}
