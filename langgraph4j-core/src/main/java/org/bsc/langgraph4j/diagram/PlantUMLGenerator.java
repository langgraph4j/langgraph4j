package org.bsc.langgraph4j.diagram;

import org.bsc.langgraph4j.DiagramGenerator;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

public class PlantUMLGenerator extends DiagramGenerator {

    private String subGraphPrefixInDeclare(Context ctx, String value  ) {
        if( !ctx.isSubGraph() ) return value;
        return ctx.titleToSnakeCase()
                .map( v -> "%s_%s".formatted( v, toSnakeCase(value) ))
                .orElse(value);
    }
    private String subGraphPrefixInCall(Context ctx, String value  ) {
        if (ctx.anySubGraphWithId(value)) return value;
        return subGraphPrefixInDeclare(ctx,value);
    }

    @Override
    protected void appendHeader( Context ctx ) {

        if( ctx.isSubGraph() ) {
            final var start = subGraphPrefixInDeclare(ctx, START);
            final var end = subGraphPrefixInDeclare(ctx, END);

            ctx.sb()
                .append("package %s {%n".formatted(ctx.title()))
                .append("circle \" \" as %s%n".formatted(start))
                .append("circle exit as %s%n".formatted(end))
            ;
            return;
        }
        ctx.sb()
            .append("@startuml %s\n".formatted( ctx.titleToSnakeCase().orElse("unnamed")))
            .append("""
            skinparam usecaseFontSize 14
            skinparam usecaseStereotypeFontSize 12
            skinparam hexagonFontSize 14
            skinparam hexagonStereotypeFontSize 12
            """)
            .append("title \"%s\"\n".formatted( ctx.title()))
            .append("""
            footer
            powered by langgraph4j
            end footer
            """)
            .append("circle start<<input>> as %s\n".formatted( START))
            .append("circle stop as %s\n".formatted( END));

    }

    @Override
    protected void appendFooter(Context ctx ) {
        if( ctx.isSubGraph() ) {
            ctx.sb().append("}\n");
            return;
        }

        ctx.sb().append("@enduml\n");

    }
    @Override
    protected void call( Context ctx, String from, String to, CallStyle style ) {
        from = subGraphPrefixInCall(ctx, from);
        to = subGraphPrefixInCall(ctx, to);

        ctx.sb().append(
                switch( style ) {
                    case CONDITIONAL ->  "\"%s\" .down.> \"%s\"\n".formatted( from, to );
                    default ->   "\"%s\" -down-> \"%s\"\n".formatted( from, to );
                });
    }
    @Override
    protected void call( Context ctx, String from, String to, String description, CallStyle style ) {
        from = subGraphPrefixInCall(ctx, from);
        to = subGraphPrefixInCall(ctx, to);

        ctx.sb().append(
                switch( style ) {
                    case CONDITIONAL ->  "\"%s\" .down.> \"%s\": \"%s\"\n".formatted( from, to, description );
                    default ->   "\"%s\" -down-> \"%s\": \"%s\"\n".formatted( from, to, description );
                });
    }
    @Override
    protected void declareConditionalStart( Context ctx, String name ) {
        name = subGraphPrefixInDeclare(ctx, name);
        ctx.sb().append("hexagon \"check state\" as %s<<Condition>>\n".formatted( name));
    }
    @Override
    protected void declareNode( Context ctx, String name ) {
        if( ctx.isSubGraph() ) {
            ctx.sb().append(  "usecase \"%s\"<<Node>> as %s%n".formatted( name, subGraphPrefixInDeclare(ctx, name) ) );
            return;
        }
        ctx.sb().append(  "usecase \"%s\"<<Node>>\n".formatted( name ) );
    }
    @Override
    protected void declareConditionalEdge( Context ctx, int ordinal ) {
        final var prefix = subGraphPrefixInDeclare(ctx, "");

        ctx.sb().append( "hexagon \"check state\" as %scondition%d<<Condition>>\n"
                            .formatted( prefix, ordinal ) );
    }

    @Override
    protected void commentLine(Context ctx, boolean yesOrNo) {
        if(yesOrNo) ctx.sb().append( "'" ) ;
    }


}
