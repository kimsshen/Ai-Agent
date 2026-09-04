package com.example.industrialai.iiot;

import com.example.industrialai.model.PushLogStatistics;

import java.util.List;

public interface PushLogDataProvider {

    PushLogStatistics findStatistics(PushLogQuery query);

    List<String> findChannels();
}
