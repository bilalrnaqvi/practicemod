package com.bilal.practicemod;

import java.util.List;

/** Optional telemetry integration point. Contacts must come from genuine obstacle data. */
public interface SubmarineInstrumentProvider {
    record Contact(float right, float forward) {}
    default List<Contact> sonar(SubmarineEntity vehicle) { return List.of(); }
    default boolean sonarOnline(SubmarineEntity vehicle) { return false; }
    default String power(SubmarineEntity vehicle) { return "OFFLINE"; }
    default String systems(SubmarineEntity vehicle) { return "OFFLINE"; }
    SubmarineInstrumentProvider STANDBY = new SubmarineInstrumentProvider() {};
}
