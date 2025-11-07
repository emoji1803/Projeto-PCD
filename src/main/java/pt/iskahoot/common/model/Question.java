package pt.iskahoot.common.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable representation of a quiz question.
 */
public final class Question {

    private final String id;
    private final String prompt;
    private final List<String> options;
    private final int correctIndex;
    private final int points;
    private final QuestionType type;

    public Question(String id,
                    String prompt,
                    List<String> options,
                    int correctIndex,
                    int points,
                    QuestionType type) {
        this.id = Objects.requireNonNullElse(id, "");
        this.prompt = Objects.requireNonNull(prompt, "prompt must not be null").trim();
        this.options = List.copyOf(Objects.requireNonNull(options, "options must not be null"));
        if (options.isEmpty()) {
            throw new IllegalArgumentException("options must not be empty");
        }
        this.correctIndex = validateCorrectIndex(correctIndex, this.options.size());
        this.points = validatePoints(points);
        this.type = Objects.requireNonNullElse(type, QuestionType.INDIVIDUAL);
    }

    private static int validateCorrectIndex(int index, int size) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException(
                "correctIndex must be between 0 (inclusive) and " + (size - 1) + " (inclusive)");
        }
        return index;
    }

    private static int validatePoints(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("points must be a positive integer");
        }
        return value;
    }

    public String id() {
        return id;
    }

    public String prompt() {
        return prompt;
    }

    public List<String> options() {
        return Collections.unmodifiableList(options);
    }

    public int correctIndex() {
        return correctIndex;
    }

    public int points() {
        return points;
    }

    public QuestionType type() {
        return type;
    }

    @Override
    public String toString() {
        return "Question{" +
            "id='" + id + '\'' +
            ", prompt='" + prompt + '\'' +
            ", options=" + options +
            ", correctIndex=" + correctIndex +
            ", points=" + points +
            ", type=" + type +
            '}';
    }
}
