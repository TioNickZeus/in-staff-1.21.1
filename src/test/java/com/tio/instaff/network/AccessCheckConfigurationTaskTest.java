package com.tio.instaff.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AccessCheckConfigurationTask Unit Tests")
class AccessCheckConfigurationTaskTest {

    @Test
    @DisplayName("Task TYPE has correct identifier")
    void testTaskTypeIdentifier() {
        assertNotNull(AccessCheckConfigurationTask.TYPE);
        assertEquals("instaff:access_check", AccessCheckConfigurationTask.TYPE.id().toString());
    }

    @Test
    @DisplayName("Task instance returns expected TYPE")
    void testTaskInstanceType() {
        AccessCheckConfigurationTask task = new AccessCheckConfigurationTask(null);
        assertEquals(AccessCheckConfigurationTask.TYPE, task.type());
    }

    @Test
    @DisplayName("Task execution handles unexpected exceptions gracefully without uncaught throw")
    void testTaskExecutionFailSafe() {
        AccessCheckConfigurationTask task = new AccessCheckConfigurationTask(null);
        assertDoesNotThrow(() -> task.start(packet -> {}));
    }
}
