package org.bsc.langgraph4j.spring.ai.agent.skill;

import org.bsc.langgraph4j.agent.skill.SkillSource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static java.util.Objects.requireNonNull;

public record SkillResource( Resource skillRootPath ) implements SkillSource {

    public static SkillResource of(Resource skillResource ) {
        return new SkillResource(skillResource);
    }

    public SkillResource {
        requireNonNull(skillRootPath, "skillRootPath cannot be null!");
    }

    @Override
    public String content() throws IOException {

        final var skillFileResource = skillRootPath.createRelative("SKILL.md");

        return skillFileResource.getContentAsString(StandardCharsets.UTF_8);
    }
}
