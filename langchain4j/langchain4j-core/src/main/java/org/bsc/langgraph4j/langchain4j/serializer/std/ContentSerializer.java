package org.bsc.langgraph4j.langchain4j.serializer.std;

import dev.langchain4j.data.message.*;
import org.bsc.langgraph4j.serializer.std.StdSerializer;
import dev.langchain4j.data.image.Image;
import org.bsc.langgraph4j.serializer.std.NullableObjectSerializer;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;


public class ContentSerializer implements NullableObjectSerializer<Content>{
    @Override
    public void write(Content object, ObjectOutput out) throws IOException {
        out.writeObject(object.type());

        switch( object.type() ) {
            case TEXT -> StdSerializer.writeUTF( ((TextContent) object).text(), out );
            case IMAGE -> {
                ImageContent imageContent = (ImageContent) object;
                out.writeObject( imageContent.detailLevel() );
                writeNullableUTF( imageContent.image().url()!=null ?
                       imageContent.image().url().toString() :
                        null, out );
                out.writeUTF( imageContent.image().mimeType() );
                StdSerializer.writeUTF( imageContent.image().base64Data(), out );
            }
            default -> throw new UnsupportedOperationException("unsupported content type: %s".formatted(object.type().name()));
        }

    }

    @Override
    public Content read(ObjectInput in) throws IOException, ClassNotFoundException {
        var type = (ContentType) in.readObject();

        return switch ( type ) {
            case TEXT -> TextContent.from(StdSerializer.readUTF(in));
            case IMAGE -> {
                var detailLevel = (ImageContent.DetailLevel) in.readObject();
                var url = readNullableUTF(in);
                var mimeType = in.readUTF();
                var base64Data = StdSerializer.readUTF(in);

                var imgBuilder = Image.builder();

                url.ifPresent(imgBuilder::url);

                imgBuilder.mimeType(mimeType)
                        .base64Data(base64Data)
                        ;
                yield ImageContent.from(imgBuilder.build(), detailLevel);
            }
            default -> throw new UnsupportedOperationException("unsupported content type: %s".formatted(type.name()));
        };
    }
}
