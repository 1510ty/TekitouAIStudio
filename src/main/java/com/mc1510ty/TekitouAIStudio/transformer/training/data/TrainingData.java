package com.mc1510ty.TekitouAIStudio.transformer.training.data;

import java.util.List;
import java.util.Objects;

/**
 * 学習データの1レコードを表す。
 *
 * Document: 事前学習用
 * Conversation: 対話学習用
 */
public sealed interface TrainingData
        permits TrainingData.Document,
        TrainingData.Conversation {

    /**
     * 事前学習用データ。
     */
    record Document(
            boolean loss,
            List<Content> content
    ) implements TrainingData {

        public Document {
            content = TrainingData.immutableList(
                    content, "content"
            );
        }
    }

    /**
     * 対話学習用データ。
     */
    record Conversation(
            List<Turn> turns
    ) implements TrainingData {

        public Conversation {
            turns = TrainingData.immutableList(
                    turns, "turns"
            );
        }
    }

    /**
     * 会話内の1つのTurn。
     */
    record Turn(
            String role,
            boolean loss,
            List<Event> events
    ) {
        public Turn {
            role = TrainingData.requireText(role, "role");
            events = TrainingData.immutableList(
                    events, "events"
            );
        }
    }

    /**
     * Turn内の1つのEvent。
     */
    record Event(
            String type,
            Boolean loss,
            List<Content> content
    ) {
        public Event {
            type = TrainingData.requireText(type, "type");
            content = TrainingData.immutableList(
                    content, "content"
            );
        }
    }

    /**
     * テキストや改行などのコンテンツ。
     */
    record Content(
            String type,
            String value,
            Boolean loss
    ) {
        public Content {
            type = TrainingData.requireText(type, "type");
        }
    }

    /**
     * Listを不変リストに変換する。
     */
    private static <T> List<T> immutableList(
            List<T> source,
            String name
    ) {
        Objects.requireNonNull(source, name);
        return List.copyOf(source);
    }

    /**
     * 必須文字列の検証。
     */
    private static String requireText(
            String value,
            String name
    ) {
        Objects.requireNonNull(value, name);

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    name + " must not be blank"
            );
        }

        return value;
    }
}
