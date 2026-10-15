package com.chargegauge;

interface IPrivilegedBatteryService {
    void destroy() = 16777114;
    String readFuelGauge() = 1;
    String readBatteryStats() = 2;
}
