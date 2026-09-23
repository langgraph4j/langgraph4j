package com.agui.json;

import com.agui.community.core.interrupt.Resume;
import com.agui.community.core.interrupt.ResumeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("AGUI JacksonSerializer Resume Test")
class AGUIJacksonSerializerResumeTest {

    private AGUIJacksonSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new AGUIJacksonSerializer();
    }

    @Test
    public void testResumeResolvedSerialization() {
        Resume resumeIn = new Resume("int-1", ResumeStatus.RESOLVED, Map.of("confirmed", true));

        var json = serializer.serialize(resumeIn);
        assertEquals("""
                {"interruptId":"int-1","status":"resolved","payload":{"confirmed":true}}""",
                json);

        Resume resumeOut = serializer.deserialize(json, Resume.class);
        assertEquals("int-1", resumeOut.interruptId());
        assertEquals(ResumeStatus.RESOLVED, resumeOut.status());
        assertEquals(Map.of("confirmed", true), resumeOut.payload());
    }

    @Test
    public void testResumeCancelledSerialization() {
        Resume resumeIn = new Resume("int-2", ResumeStatus.CANCELLED, null);

        var json = serializer.serialize(resumeIn);
        assertEquals("""
                {"interruptId":"int-2","status":"cancelled"}""",
                json);

        Resume resumeOut = serializer.deserialize(json, Resume.class);
        assertEquals("int-2", resumeOut.interruptId());
        assertEquals(ResumeStatus.CANCELLED, resumeOut.status());
        assertNull(resumeOut.payload());
    }
}
