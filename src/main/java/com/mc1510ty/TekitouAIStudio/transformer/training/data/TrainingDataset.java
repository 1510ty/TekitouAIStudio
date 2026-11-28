
package com.mc1510ty.TekitouAIStudio.transformer.training.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class TrainingDataset implements AutoCloseable {

    /**
     * データセットの種類。
     */
    public enum Type {

        PRETRAINING(".zizenn.jsonl", "document"),

        CONVERSATION(".taiwa.jsonl", "conversation");

        private final String fileSuffix;
        private final String recordType;

        Type(String fileSuffix, String recordType) {
            this.fileSuffix = fileSuffix;
            this.recordType = recordType;
        }

        public String getFileSuffix() {
            return fileSuffix;
        }

        public String getRecordType() {
            return recordType;
        }
    }

    private final Type type;
    private final List<Path> files;

    private int nextFileIndex = 0;

    private Path currentFile;
    private TrainingDataLoader currentLoader;

    private long recordsRead = 0;

    private boolean closed = false;

    /**
     * データセットを初期化する。
     *
     * 指定ディレクトリから対象のJSONLファイルを検出する。
     */
    public TrainingDataset(
            Path directory,
            Type type
    ) throws IOException {

        Objects.requireNonNull(directory, "directory");
        this.type = Objects.requireNonNull(type, "type");

        if (!Files.isDirectory(directory)) {
            throw new IOException(
                    "データセットディレクトリが存在しません: "
                            + directory
            );
        }

        try (var stream = Files.list(directory)) {

            files = stream
                    .filter(Files::isRegularFile)
                    .filter(path ->
                            path.getFileName()
                                    .toString()
                                    .endsWith(type.getFileSuffix())
                    )
                    .sorted(
                            Comparator.comparing(
                                    path -> path.getFileName().toString()
                            )
                    )
                    .toList();
        }

        if (files.isEmpty()) {
            throw new IOException(
                    "対象の学習データファイルが見つかりません: "
                            + type.getFileSuffix()
            );
        }
    }

    /**
     * 次の学習データを取得する。
     *
     * 現在のファイルが終了すると、自動的に次のファイルへ移動する。
     *
     * @return TrainingData。全ファイルの読み込み終了時はnull。
     */
    public TrainingData readNext() throws IOException {

        if (closed) {
            throw new IOException(
                    "TrainingDatasetは閉じられています"
            );
        }

        while (true) {

            // 次のファイルを開く
            if (currentLoader == null) {

                if (nextFileIndex >= files.size()) {
                    return null;
                }

                currentFile = files.get(nextFileIndex++);

                try {
                    currentLoader =
                            new TrainingDataLoader(currentFile);

                } catch (IOException e) {
                    throw new IOException(
                            "ファイルを開けません: " + currentFile,
                            e
                    );
                }
            }

            TrainingData data;

            try {
                data = currentLoader.readNext();

            } catch (IOException e) {

                throw new IOException(
                        "ファイル: " + currentFile.getFileName()
                                + " の読み込みに失敗しました: "
                                + e.getMessage(),
                        e
                );
            }

            // 現在のファイルが終了した
            if (data == null) {

                closeCurrentLoader();
                currentFile = null;

                continue;
            }

            // ファイル名とレコード形式を照合する
            validateRecordType(data);

            recordsRead++;

            return data;
        }
    }

    /**
     * JSONLのファイル名とrecord_typeの整合性を確認する。
     */
    private void validateRecordType(
            TrainingData data
    ) throws IOException {

        boolean valid = switch (type) {

            case PRETRAINING ->
                    data instanceof TrainingData.Document;

            case CONVERSATION ->
                    data instanceof TrainingData.Conversation;
        };

        if (!valid) {

            String actualType =
                    data instanceof TrainingData.Document
                            ? "document"
                            : "conversation";

            throw new IOException(
                    "ファイル: " + currentFile.getFileName()
                            + " の " + currentLoader.getLineNumber()
                            + "行目: record_typeが不一致です。"
                            + "期待値: " + type.getRecordType()
                            + ", 実際: " + actualType
            );
        }
    }

    /**
     * 現在開いているTrainingDataLoaderを閉じる。
     */
    private void closeCurrentLoader() throws IOException {

        if (currentLoader != null) {
            currentLoader.close();
            currentLoader = null;
        }
    }

    /**
     * データセットの種類を取得する。
     */
    public Type getType() {
        return type;
    }

    /**
     * 検出されたファイル一覧を取得する。
     */
    public List<Path> getFiles() {
        return files;
    }

    /**
     * ファイル数を取得する。
     */
    public int getFileCount() {
        return files.size();
    }

    /**
     * 読み込み済みレコード数を取得する。
     */
    public long getRecordsRead() {
        return recordsRead;
    }

    /**
     * 現在読み込み中のファイルを取得する。
     */
    public Path getCurrentFile() {
        return currentFile;
    }

    @Override
    public void close() throws IOException {

        if (!closed) {
            closed = true;
            closeCurrentLoader();
            currentFile = null;
        }
    }
}
