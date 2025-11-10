package pt.iskahoot.server.repository;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import pt.iskahoot.common.model.Question;
import pt.iskahoot.common.model.QuestionType;
import pt.iskahoot.common.model.Quiz;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Carrega definições de quizzes a partir de ficheiros JSON, respeitando o
 * formato descrito no enunciado.
 */
public final class QuizRepository {

    private final Gson gson;

    public QuizRepository(Gson gson) {
        this.gson = Objects.requireNonNull(gson, "gson must not be null");
    }

    public Quiz load(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        if (!Files.exists(path)) {
            throw new IOException("Quiz file not found: " + path);
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement root = JsonParser.parseReader(reader);
            return parseQuiz(root);
        } catch (JsonParseException ex) {
            throw new IOException("Failed to parse quiz file " + path + ": " + ex.getMessage(), ex);
        }
    }

    private Quiz parseQuiz(JsonElement rootElement) {
        if (!rootElement.isJsonObject()) {
            throw new JsonParseException("Expected root JSON object");
        }

        JsonObject root = rootElement.getAsJsonObject();
        if (root.has("quizzes")) {
            var quizzesElement = root.getAsJsonArray("quizzes");
            if (quizzesElement.isEmpty()) {
                throw new JsonParseException("quizzes array must contain at least one quiz");
            }
            return parseQuizObject(quizzesElement.get(0).getAsJsonObject());
        }

        return parseQuizObject(root);
    }

    private Quiz parseQuizObject(JsonObject quizObject) {
        String name = quizObject.has("name") ? quizObject.get("name").getAsString() : "default";
        if (!quizObject.has("questions")) {
            throw new JsonParseException("quiz object must contain a questions array");
        }
        var questionsArray = quizObject.getAsJsonArray("questions");
        if (questionsArray.isEmpty()) {
            throw new JsonParseException("questions array must contain at least one entry");
        }

        List<Question> questions = new ArrayList<>();
        for (JsonElement element : questionsArray) {
            questions.add(parseQuestion(element.getAsJsonObject()));
        }

        return new Quiz(name, questions);
    }

    private Question parseQuestion(JsonObject object) {
        String id = object.has("id") ? object.get("id").getAsString() : "";
        String prompt = object.get("question").getAsString();

        List<String> options = gson.fromJson(
            object.getAsJsonArray("options"),
            new TypeToken<List<String>>() {
            }.getType());
        if (options == null || options.isEmpty()) {
            throw new JsonParseException("question options must not be empty");
        }

        int correctIndex = object.get("correct").getAsInt();
        // O enunciado define índices a partir de 1; convertemos para base 0 usada internamente.
        correctIndex = correctIndex - 1;
        if (correctIndex < 0 || correctIndex >= options.size()) {
            throw new JsonParseException("correct index out of bounds for question: " + prompt);
        }

        int points = object.get("points").getAsInt();
        QuestionType type = QuestionType.INDIVIDUAL;
        if (object.has("type")) {
            String rawType = object.get("type").getAsString();
            type = QuestionType.valueOf(rawType.toUpperCase(Locale.ROOT));
        }

        return new Question(id, prompt, options, correctIndex, points, type);
    }
}
