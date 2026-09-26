package org.bsc.langgraph4j.spring.ai.serializer.std;

import org.bsc.langgraph4j.serializer.std.StdSerializer;
import org.springframework.ai.chat.messages.SystemMessage;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

class SystemMessageSerializer implements StdSerializer<SystemMessage> {

    @Override
    public void write(SystemMessage object, ObjectOutput out) throws IOException {
        var text = Objects.requireNonNull( object.getText(), "text cannot be null" );
        StdSerializer.writeUTF( text, out );

    }

    @Override
    public SystemMessage read(ObjectInput in) throws IOException, ClassNotFoundException {
        var text = StdSerializer.readUTF(in);
        return new SystemMessage( text );
    }
}
