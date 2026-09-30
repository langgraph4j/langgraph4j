package org.bsc.langgraph4j;

import org.bsc.langgraph4j.checkpoint.Checkpoint;

import java.util.Map;

public sealed interface GraphInput permits GraphArgs, GraphResume {

    Map<String, Object> value();

    static GraphInput resume() { return new GraphResume();}
    static GraphInput resume( Map<String,Object> value ) { return new GraphResume(value); }
    static GraphInput resume( Checkpoint checkpoint ) { return new GraphResume(checkpoint); }

    static GraphInput args( Map<String,Object> value) {
        return new GraphArgs(value);
    }
    static GraphInput noArgs() {
        return new GraphArgs();
    }
}

