package org.bsc.langgraph4j.agent.skill;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public record SkillPath(Path skillRootPath ) implements SkillSource {


    public static SkillPath of(Path skillRootPath ) {
        return new SkillPath(skillRootPath);
    }

    public SkillPath {
        requireNonNull(skillRootPath, "skillRootPath cannot be null!");
    }

    @Override
    public String content() throws IOException {

        final var skillFilePath = skillRootPath.resolve("SKILL.md");

        return Files.readString( skillFilePath, StandardCharsets.UTF_8);
    }
}
