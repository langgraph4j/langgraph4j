package org.bsc.langgraph4j.spring.ai.serializer.std;

import org.bsc.langgraph4j.serializer.std.StdSerializer;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class UserMessageSerializer implements StdSerializer<UserMessage> {

    @Override
    public void write(UserMessage object, ObjectOutput out) throws IOException {
        Objects.requireNonNull(object.getText(), "text cannot be null");
        StdSerializer.writeUTF(object.getText(), out);
        out.writeObject(object.getMedia());
        out.writeObject(object.getMetadata());
    }

    @Override
    @SuppressWarnings("unchecked")
    public UserMessage read(ObjectInput in) throws IOException, ClassNotFoundException {
        var text = StdSerializer.readUTF(in);
        var media = (List<Media>) in.readObject();
        var metadata = (Map<String, Object>) in.readObject();
        return UserMessage.builder()
                    .text(text)
                    .media( media )
                    .metadata(metadata)
                    .build();
    }
}
