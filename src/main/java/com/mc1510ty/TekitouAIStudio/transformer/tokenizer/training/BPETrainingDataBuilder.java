package com.mc1510ty.TekitouAIStudio.transformer.tokenizer.training;

import com.mc1510ty.TekitouAIStudio.transformer.tokenizer.SpecialTokens;
import com.mc1510ty.TekitouAIStudio.transformer.tokenizer.Tokenizer;
import com.mc1510ty.TekitouAIStudio.transformer.training.data.TrainingData;
import com.mc1510ty.TekitouAIStudio.transformer.training.data.TrainingDataset;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class BPETrainingDataBuilder {

    private final Tokenizer tokenizer;

    public BPETrainingDataBuilder(Tokenizer tokenizer) {
        this.tokenizer = Objects.requireNonNull(
                tokenizer,
                "tokenizer must not be null"
        );
    }

    /**
     * TrainingDatasetからBPE学習用Sequenceを構築する。
     *
     * 複数のデータセットを指定することもできる。
     * TrainingDatasetのcloseは呼び出し側が行う。
     */
    public List<int[]> build(
            TrainingDataset... datasets
    ) throws IOException {

        Objects.requireNonNull(
                datasets,
                "datasets must not be null"
        );

        SequenceAssembler assembler =
                new SequenceAssembler();

        for (TrainingDataset dataset : datasets) {

            Objects.requireNonNull(
                    dataset,
                    "dataset must not be null"
            );

            TrainingData data;

            while ((data = dataset.readNext()) != null) {
                appendRecord(data, assembler);
            }
        }

        return assembler.finishAll();
    }

    /**
     * TrainingDataのコレクションからSequenceを構築する。
     * テストやメモリ上のデータを処理する場合に使用する。
     */
    List<int[]> buildFromRecords(
            Iterable<? extends TrainingData> records
    ) {
        Objects.requireNonNull(
                records,
                "records must not be null"
        );

        SequenceAssembler assembler =
                new SequenceAssembler();

        for (TrainingData data : records) {
            appendRecord(
                    Objects.requireNonNull(
                            data,
                            "record must not be null"
                    ),
                    assembler
            );
        }

        return assembler.finishAll();
    }

    /**
     * DocumentまたはConversationを処理する。
     */
    private void appendRecord(
            TrainingData data,
            SequenceAssembler assembler
    ) {
        if (data instanceof TrainingData.Document document) {

            appendContents(
                    document.content(),
                    document.loss(),
                    assembler
            );

            // Document境界
            assembler.finishSequence();

        } else if (data instanceof TrainingData.Conversation conversation) {

            for (TrainingData.Turn turn : conversation.turns()) {

                for (TrainingData.Event event : turn.events()) {

                    boolean eventLoss = event.loss() != null
                            ? event.loss()
                            : turn.loss();

                    appendContents(
                            event.content(),
                            eventLoss,
                            assembler
                    );

                    // Event境界
                    assembler.finishSequence();
                }
            }
        }
    }

    /**
     * Content一覧を処理する。
     *
     * LossはContent > Event > Turn、
     * またはContent > Documentの優先順位で決定する。
     */
    private void appendContents(
            List<TrainingData.Content> contents,
            boolean inheritedLoss,
            SequenceAssembler assembler
    ) {
        for (TrainingData.Content content : contents) {

            boolean effectiveLoss = content.loss() != null
                    ? content.loss()
                    : inheritedLoss;

            assembler.selectLoss(effectiveLoss);

            switch (content.type()) {

                case "text" -> {

                    if (content.value() == null) {
                        throw new IllegalArgumentException(
                                "text content value must not be null"
                        );
                    }

                    assembler.appendText(content.value());
                }

                case "line_break" -> {
                    assembler.appendLineBreak();
                }

                case "image", "audio", "video" -> {
                    // 現時点ではテキスト語彙学習の対象外
                    assembler.finishSequence();
                }

                default -> throw new IllegalArgumentException(
                        "Unsupported content type: " + content.type()
                );
            }
        }
    }

    /**
     * Sequenceを組み立てる内部クラス。
     */
    private final class SequenceAssembler {

        private final List<int[]> sequences =
                new ArrayList<>();

        private final StringBuilder pendingText =
                new StringBuilder();

        private int[] tokenBuffer = new int[256];
        private int tokenCount = 0;

        private Boolean currentLoss;

        /**
         * Loss状態を切り替える。
         * 状態が変化した場合はSequenceを分割する。
         */
        private void selectLoss(boolean loss) {

            if (currentLoss != null
                    && currentLoss.booleanValue() != loss) {

                finishSequence();
            }

            currentLoss = loss;
        }

        /**
         * テキストを追加する。
         *
         * 隣接するText Contentは連結してから
         * Tokenizerに渡す。
         */
        private void appendText(String text) {
            pendingText.append(text);
        }

        /**
         * テキストをTokenizerでエンコードする。
         */
        private void flushText() {

            if (pendingText.length() == 0) {
                return;
            }

            int[] encoded = tokenizer.encodeText(
                    pendingText.toString()
            );

            appendTokens(encoded);

            pendingText.setLength(0);
        }

        /**
         * 特殊トークンとして改行を追加する。
         */
        private void appendLineBreak() {
            flushText();

            appendToken(SpecialTokens.LINE_BREAK_ID);
        }

        /**
         * Token IDを追加する。
         */
        private void appendToken(int tokenId) {
            ensureCapacity(1);

            tokenBuffer[tokenCount++] = tokenId;
        }

        /**
         * Token ID配列を追加する。
         */
        private void appendTokens(int[] tokens) {

            Objects.requireNonNull(
                    tokens,
                    "encoded tokens must not be null"
            );

            ensureCapacity(tokens.length);

            System.arraycopy(
                    tokens,
                    0,
                    tokenBuffer,
                    tokenCount,
                    tokens.length
            );

            tokenCount += tokens.length;
        }

        /**
         * バッファの容量を確保する。
         */
        private void ensureCapacity(int additionalCount) {

            int required = Math.addExact(
                    tokenCount,
                    additionalCount
            );

            if (required <= tokenBuffer.length) {
                return;
            }

            int newCapacity = tokenBuffer.length;

            while (newCapacity < required) {

                int nextCapacity = newCapacity * 2;

                if (nextCapacity <= newCapacity) {
                    newCapacity = required;
                    break;
                }

                newCapacity = nextCapacity;
            }

            tokenBuffer = Arrays.copyOf(
                    tokenBuffer,
                    newCapacity
            );
        }

        /**
         * 現在のSequenceを確定する。
         */
        private void finishSequence() {

            flushText();

            if (tokenCount > 0) {

                sequences.add(
                        Arrays.copyOf(
                                tokenBuffer,
                                tokenCount
                        )
                );
            }

            tokenCount = 0;
            currentLoss = null;
        }

        /**
         * 全Sequenceを返す。
         */
        private List<int[]> finishAll() {
            finishSequence();

            return List.copyOf(sequences);
        }
    }
}