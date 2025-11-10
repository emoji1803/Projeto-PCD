package pt.iskahoot.common.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Um quiz é o conjunto de perguntas que pode alimentar vários jogos.
 */
public final class Quiz {

    private final String name;
    private final List<Question> questions;

    public Quiz(String name, List<Question> questions) {
        this.name = Objects.requireNonNullElse(name, "default");
        Objects.requireNonNull(questions, "questions must not be null");
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("questions must not be empty");
        }
        this.questions = List.copyOf(questions);
    }

    public String name() {
        return name;
    }

    public List<Question> questions() {
        return Collections.unmodifiableList(questions);
    }

    @Override
    public String toString() {
        return "Quiz{" +
            "name='" + name + '\'' +
            ", questions=" + questions.size() +
            '}';
    }
}
