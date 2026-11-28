
package com.mc1510ty.TekitouAIStudio.transformer.training.data;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TrainingDataLoader implements AutoCloseable {

    private final BufferedReader reader;
    private final JsonFactory jsonFactory;

    private long lineNumber = 0;
    private boolean closed = false;

    public TrainingDataLoader(Path path) throws IOException {

        Objects.requireNonNull(path, "path");

        reader = Files.newBufferedReader(
                path,
                StandardCharsets.UTF_8
        );

        jsonFactory = new JsonFactory();
    }

    /**
     * JSONLから次の1レコードを読み込む。
     *
     * @return TrainingData、ファイル終端ならnull
     */
    public TrainingData readNext() throws IOException {

        if (closed) {
            throw new IOException("TrainingDataLoaderは閉じられています");
        }

        String line;

        while ((line = reader.readLine()) != null) {

            lineNumber++;

            // 空行は読み飛ばす
            if (line.isBlank()) {
                continue;
            }

            // UTF-8 BOMへの対応
            if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }

            try (JsonParser parser =
                         jsonFactory.createParser(line)) {

                JsonToken token = parser.nextToken();

                if (token != JsonToken.START_OBJECT) {
                    throw new IOException(
                            "JSONのルートはObjectである必要があります"
                    );
                }

                TrainingData data = readRecord(parser);

                // 1行にJSONが複数存在しないか確認
                if (parser.nextToken() != null) {
                    throw new IOException(
                            "1行に複数のJSON値が存在します"
                    );
                }

                return data;

            } catch (IOException e) {

                throw new IOException(
                        "JSONL " + lineNumber +
                                "行目: " + e.getMessage(),
                        e
                );
            }
        }

        return null;
    }

    /**
     * 1レコードを解析する。
     */
    private TrainingData readRecord(
            JsonParser parser
    ) throws IOException {

        String recordType = null;

        Boolean documentLoss = null;

        List<TrainingData.Content> content = null;
        List<TrainingData.Turn> turns = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {

            String field = parser.currentName();

            if (field == null) {
                throw new IOException("不正なJSON構造です");
            }

            JsonToken token = parser.nextToken();

            if (token == null) {
                throw new IOException("JSONが途中で終了しています");
            }

            switch (field) {

                case "record_type" ->
                        recordType = readString(parser, token);

                case "loss" ->
                        documentLoss = readRequiredBoolean(
                                parser, token
                        );

                case "content" ->
                        content = readContentArray(parser, token);

                case "turns" ->
                        turns = readTurnArray(parser, token);

                default -> parser.skipChildren();
            }
        }

        if (recordType == null) {
            throw new IOException(
                    "record_typeが指定されていません"
            );
        }

        return switch (recordType) {

            case "document" -> {

                if (documentLoss == null || content == null) {
                    throw new IOException(
                            "Documentに必要な項目がありません"
                    );
                }

                if (turns != null) {
                    throw new IOException(
                            "Documentにturnsは指定できません"
                    );
                }

                yield new TrainingData.Document(
                        documentLoss,
                        content
                );
            }

            case "conversation" -> {

                if (turns == null) {
                    throw new IOException(
                            "Conversationにturnsがありません"
                    );
                }

                if (documentLoss != null || content != null) {
                    throw new IOException(
                            "ConversationにDocument用の項目があります"
                    );
                }

                yield new TrainingData.Conversation(turns);
            }

            default -> throw new IOException(
                    "未対応のrecord_type: " + recordType
            );
        };
    }

    /**
     * Turn配列を解析する。
     */
    private List<TrainingData.Turn> readTurnArray(
            JsonParser parser,
            JsonToken token
    ) throws IOException {

        requireArray(token, "turns");

        List<TrainingData.Turn> turns = new ArrayList<>();

        while (parser.nextToken() != JsonToken.END_ARRAY) {

            if (parser.currentToken() != JsonToken.START_OBJECT) {
                throw new IOException(
                        "turnsの要素はObjectである必要があります"
                );
            }

            turns.add(readTurn(parser));
        }

        return turns;
    }

    /**
     * Turnを解析する。
     */
    private TrainingData.Turn readTurn(
            JsonParser parser
    ) throws IOException {

        String role = null;
        Boolean loss = null;

        List<TrainingData.Event> events = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {

            String field = parser.currentName();
            JsonToken token = parser.nextToken();

            switch (field) {

                case "role" ->
                        role = readString(parser, token);

                case "loss" ->
                        loss = readRequiredBoolean(parser, token);

                case "events" ->
                        events = readEventArray(parser, token);

                default -> parser.skipChildren();
            }
        }

        if (role == null || loss == null || events == null) {
            throw new IOException(
                    "Turnに必要な項目がありません"
            );
        }

        return new TrainingData.Turn(
                role,
                loss,
                events
        );
    }

    /**
     * Event配列を解析する。
     */
    private List<TrainingData.Event> readEventArray(
            JsonParser parser,
            JsonToken token
    ) throws IOException {

        requireArray(token, "events");

        List<TrainingData.Event> events = new ArrayList<>();

        while (parser.nextToken() != JsonToken.END_ARRAY) {

            if (parser.currentToken() != JsonToken.START_OBJECT) {
                throw new IOException(
                        "eventsの要素はObjectである必要があります"
                );
            }

            events.add(readEvent(parser));
        }

        return events;
    }

    /**
     * Eventを解析する。
     */
    private TrainingData.Event readEvent(
            JsonParser parser
    ) throws IOException {

        String type = null;
        Boolean loss = null;

        List<TrainingData.Content> content = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {

            String field = parser.currentName();
            JsonToken token = parser.nextToken();

            switch (field) {

                case "type" ->
                        type = readString(parser, token);

                case "loss" ->
                        loss = readNullableBoolean(parser, token);

                case "content" ->
                        content = readContentArray(parser, token);

                default -> parser.skipChildren();
            }
        }

        if (type == null || content == null) {
            throw new IOException(
                    "Eventに必要な項目がありません"
            );
        }

        return new TrainingData.Event(
                type,
                loss,
                content
        );
    }

    /**
     * Content配列を解析する。
     */
    private List<TrainingData.Content> readContentArray(
            JsonParser parser,
            JsonToken token
    ) throws IOException {

        requireArray(token, "content");

        List<TrainingData.Content> contents = new ArrayList<>();

        while (parser.nextToken() != JsonToken.END_ARRAY) {

            if (parser.currentToken() != JsonToken.START_OBJECT) {
                throw new IOException(
                        "contentの要素はObjectである必要があります"
                );
            }

            contents.add(readContent(parser));
        }

        return contents;
    }

    /**
     * Contentを解析する。
     */
    private TrainingData.Content readContent(
            JsonParser parser
    ) throws IOException {

        String type = null;
        String value = null;

        Boolean loss = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {

            String field = parser.currentName();
            JsonToken token = parser.nextToken();

            switch (field) {

                case "type" ->
                        type = readString(parser, token);

                case "value" ->
                        value = readNullableString(parser, token);

                case "loss" ->
                        loss = readNullableBoolean(parser, token);

                default -> parser.skipChildren();
            }
        }

        if (type == null) {
            throw new IOException(
                    "Contentにtypeがありません"
            );
        }

        if (type.equals("text") && value == null) {
            throw new IOException(
                    "text型Contentにvalueがありません"
            );
        }

        return new TrainingData.Content(
                type,
                value,
                loss
        );
    }

    /**
     * 必須の文字列を取得する。
     */
    private String readString(
            JsonParser parser,
            JsonToken token
    ) throws IOException {

        if (token != JsonToken.VALUE_STRING) {
            throw new IOException(
                    "文字列が必要ですが、" + token + "が指定されました"
            );
        }

        return parser.getText();
    }

    /**
     * nullを許可する文字列を取得する。
     */
    private String readNullableString(
            JsonParser parser,
            JsonToken token
    ) throws IOException {

        if (token == JsonToken.VALUE_NULL) {
            return null;
        }

        return readString(parser, token);
    }

    /**
     * 必須のBooleanを取得する。
     */
    private boolean readRequiredBoolean(
            JsonParser parser,
            JsonToken token
    ) throws IOException {

        return switch (token) {

            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;

            default -> throw new IOException(
                    "Booleanが必要ですが、" + token + "が指定されました"
            );
        };
    }

    /**
     * nullを許可するBooleanを取得する。
     */
    private Boolean readNullableBoolean(
            JsonParser parser,
            JsonToken token
    ) throws IOException {

        if (token == JsonToken.VALUE_NULL) {
            return null;
        }

        return readRequiredBoolean(parser, token);
    }

    /**
     * 配列型であることを確認する。
     */
    private void requireArray(
            JsonToken token,
            String name
    ) throws IOException {

        if (token != JsonToken.START_ARRAY) {
            throw new IOException(
                    name + "はArrayである必要があります"
            );
        }
    }

    /**
     * 現在の行番号を取得する。
     */
    public long getLineNumber() {
        return lineNumber;
    }

    @Override
    public void close() throws IOException {

        if (!closed) {
            closed = true;
            reader.close();
        }
    }
}
