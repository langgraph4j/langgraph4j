package org.bsc.langgraph4j;

import java.util.Map;

public sealed interface GraphInput permits GraphArgs, GraphResume {

    static GraphInput resume() {
        return new GraphResume();
    }
    static GraphInput resume( Map<String,Object> value ) { return new GraphResume(value); }

    static GraphInput args( Map<String,Object> value) {
        return new GraphArgs(value);
    }
    static GraphInput noArgs() {
        return new GraphArgs();
    }
}

