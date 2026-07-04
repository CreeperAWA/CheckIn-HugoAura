<script setup>
import * as Diff from 'diff';

const props = defineProps({
    oldQuestion: {
        type: Object,
        required: true
    },
    newQuestion: {
        type: Object,
        required: true
    }
});

function computeLineDiff(oldText, newText) {
    if (!oldText) oldText = '';
    if (!newText) newText = '';
    const changes = Diff.diffLines(oldText, newText);
    const result = [];
    for (const change of changes) {
        if (change.added) {
            for (const line of change.value.replace(/\n$/, '').split('\n')) {
                result.push({ type: 'add', text: line });
            }
        } else if (change.removed) {
            for (const line of change.value.replace(/\n$/, '').split('\n')) {
                result.push({ type: 'remove', text: line });
            }
        } else {
            for (const line of change.value.replace(/\n$/, '').split('\n')) {
                result.push({ type: 'equal', text: line });
            }
        }
    }
    return result;
}

function computeWordDiff(oldText, newText) {
    if (!oldText) oldText = '';
    if (!newText) newText = '';
    const changes = Diff.diffWords(oldText, newText);
    return changes.map(change => ({
        type: change.added ? 'add' : change.removed ? 'remove' : 'equal',
        text: change.value
    }));
}

const contentDiff = computed(() => computeLineDiff(props.oldQuestion.content, props.newQuestion.content));
const explanationDiff = computed(() => computeLineDiff(props.oldQuestion.explanation || '', props.newQuestion.explanation || ''));

const hasContentChange = computed(() => {
    return contentDiff.value.some(d => d.type !== 'equal');
});

const hasExplanationChange = computed(() => {
    return explanationDiff.value.some(d => d.type !== 'equal');
});

const choicesDiff = computed(() => {
    const oldChoices = props.oldQuestion.choices || [];
    const newChoices = props.newQuestion.choices || [];
    const maxLen = Math.max(oldChoices.length, newChoices.length);
    const result = [];

    for (let i = 0; i < maxLen; i++) {
        const oldC = oldChoices[i];
        const newC = newChoices[i];

        if (!oldC && newC) {
            result.push({ type: 'add', newChoice: newC });
        } else if (oldC && !newC) {
            result.push({ type: 'remove', oldChoice: oldC });
        } else if (oldC && newC) {
            const contentDiff = computeWordDiff(oldC.content, newC.content);
            const contentChanged = contentDiff.some(d => d.type !== 'equal');
            const correctChanged = oldC.correct !== newC.correct;
            if (contentChanged || correctChanged) {
                result.push({
                    type: 'modify',
                    oldChoice: oldC,
                    newChoice: newC,
                    contentDiff,
                    correctChanged
                });
            } else {
                result.push({ type: 'equal', choice: oldC });
            }
        }
    }
    return result;
});

const hasChoicesChange = computed(() => {
    return choicesDiff.value.some(d => d.type !== 'equal');
});

const subQuestionsDiff = computed(() => {
    const oldQuestions = props.oldQuestion.questions || [];
    const newQuestions = props.newQuestion.questions || [];
    const maxLen = Math.max(oldQuestions.length, newQuestions.length);
    const result = [];

    for (let i = 0; i < maxLen; i++) {
        const oldQ = oldQuestions[i];
        const newQ = newQuestions[i];

        if (!oldQ && newQ) {
            result.push({ type: 'add', newQuestion: newQ });
        } else if (oldQ && !newQ) {
            result.push({ type: 'remove', oldQuestion: oldQ });
        } else if (oldQ && newQ) {
            const contentDiff = computeLineDiff(oldQ.content || '', newQ.content || '');
            const contentChanged = contentDiff.some(d => d.type !== 'equal');
            const oldChoices = oldQ.choices || [];
            const newChoices = newQ.choices || [];
            let choicesChanged = oldChoices.length !== newChoices.length;
            if (!choicesChanged) {
                for (let j = 0; j < oldChoices.length; j++) {
                    if (oldChoices[j].content !== newChoices[j].content || oldChoices[j].correct !== newChoices[j].correct) {
                        choicesChanged = true;
                        break;
                    }
                }
            }
            if (contentChanged || choicesChanged) {
                result.push({
                    type: 'modify',
                    index: i + 1,
                    oldQuestion: oldQ,
                    newQuestion: newQ,
                    contentDiff,
                    choicesChanged
                });
            } else {
                result.push({ type: 'equal', index: i + 1, question: oldQ });
            }
        }
    }
    return result;
});

const hasSubQuestionsChange = computed(() => {
    return subQuestionsDiff.value.some(d => d.type !== 'equal');
});

const hasAnyChange = computed(() => {
    return hasContentChange.value || hasExplanationChange.value || hasChoicesChange.value || hasSubQuestionsChange.value;
});
</script>

<template>
    <div class="diff-viewer">
        <div v-if="!hasAnyChange" class="no-changes">
            <el-text type="info">两个版本之间没有差异</el-text>
        </div>

        <template v-if="hasContentChange">
            <div class="diff-section">
                <div class="diff-section-title">
                    <el-text tag="b">题目内容变更</el-text>
                </div>
                <div class="diff-content">
                    <div v-for="(line, idx) in contentDiff" :key="idx"
                         class="diff-line"
                         :class="{ 'diff-add': line.type === 'add', 'diff-remove': line.type === 'remove' }">
                        <span class="diff-line-prefix">{{ line.type === 'add' ? '+' : line.type === 'remove' ? '-' : ' ' }}</span>
                        <span class="diff-line-text">{{ line.text }}</span>
                    </div>
                </div>
            </div>
        </template>

        <template v-if="hasChoicesChange">
            <div class="diff-section">
                <div class="diff-section-title">
                    <el-text tag="b">选项变更</el-text>
                </div>
                <div class="diff-choices">
                    <template v-for="(item, idx) in choicesDiff" :key="idx">
                        <div v-if="item.type === 'add'" class="diff-choice diff-add">
                            <el-tag type="success" size="small" class="diff-choice-tag">新增</el-tag>
                            <span class="diff-choice-correct" :class="{ correct: item.newChoice.correct }">
                                {{ item.newChoice.correct ? '正确' : '错误' }}
                            </span>
                            <span>{{ item.newChoice.content }}</span>
                        </div>
                        <div v-else-if="item.type === 'remove'" class="diff-choice diff-remove">
                            <el-tag type="danger" size="small" class="diff-choice-tag">删除</el-tag>
                            <span class="diff-choice-correct" :class="{ correct: item.oldChoice.correct }">
                                {{ item.oldChoice.correct ? '正确' : '错误' }}
                            </span>
                            <span>{{ item.oldChoice.content }}</span>
                        </div>
                        <div v-else-if="item.type === 'modify'" class="diff-choice diff-modify">
                            <el-tag type="warning" size="small" class="diff-choice-tag">修改</el-tag>
                            <span v-if="item.correctChanged" class="diff-correct-change">
                                <span class="diff-remove-inline">{{ item.oldChoice.correct ? '正确' : '错误' }}</span>
                                <span class="diff-add-inline">{{ item.newChoice.correct ? '正确' : '错误' }}</span>
                            </span>
                            <span v-else class="diff-choice-correct" :class="{ correct: item.newChoice.correct }">
                                {{ item.newChoice.correct ? '正确' : '错误' }}
                            </span>
                            <span class="diff-word-content">
                                <template v-for="(part, pi) in item.contentDiff" :key="pi">
                                    <span v-if="part.type === 'add'" class="diff-add-inline">{{ part.text }}</span>
                                    <span v-else-if="part.type === 'remove'" class="diff-remove-inline">{{ part.text }}</span>
                                    <span v-else>{{ part.text }}</span>
                                </template>
                            </span>
                        </div>
                        <div v-else class="diff-choice">
                            <span class="diff-choice-correct" :class="{ correct: item.choice.correct }">
                                {{ item.choice.correct ? '正确' : '错误' }}
                            </span>
                            <span>{{ item.choice.content }}</span>
                        </div>
                    </template>
                </div>
            </div>
        </template>

        <template v-if="hasExplanationChange">
            <div class="diff-section">
                <div class="diff-section-title">
                    <el-text tag="b">解析变更</el-text>
                </div>
                <div class="diff-content">
                    <div v-for="(line, idx) in explanationDiff" :key="idx"
                         class="diff-line"
                         :class="{ 'diff-add': line.type === 'add', 'diff-remove': line.type === 'remove' }">
                        <span class="diff-line-prefix">{{ line.type === 'add' ? '+' : line.type === 'remove' ? '-' : ' ' }}</span>
                        <span class="diff-line-text">{{ line.text }}</span>
                    </div>
                </div>
            </div>
        </template>

        <template v-if="hasSubQuestionsChange">
            <div class="diff-section">
                <div class="diff-section-title">
                    <el-text tag="b">子题目变更</el-text>
                </div>
                <div class="diff-sub-questions">
                    <template v-for="(item, idx) in subQuestionsDiff" :key="idx">
                        <div v-if="item.type === 'add'" class="diff-sub-question diff-add">
                            <el-tag type="success" size="small">新增子题目 #{{ idx + 1 }}</el-tag>
                            <div class="diff-sub-content">{{ item.newQuestion.content }}</div>
                        </div>
                        <div v-else-if="item.type === 'remove'" class="diff-sub-question diff-remove">
                            <el-tag type="danger" size="small">删除子题目 #{{ idx + 1 }}</el-tag>
                            <div class="diff-sub-content">{{ item.oldQuestion.content }}</div>
                        </div>
                        <div v-else-if="item.type === 'modify'" class="diff-sub-question diff-modify">
                            <el-tag type="warning" size="small">修改子题目 #{{ item.index }}</el-tag>
                            <div class="diff-content" style="margin-top: 4px">
                                <div v-for="(line, li) in item.contentDiff" :key="li"
                                     class="diff-line"
                                     :class="{ 'diff-add': line.type === 'add', 'diff-remove': line.type === 'remove' }">
                                    <span class="diff-line-prefix">{{ line.type === 'add' ? '+' : line.type === 'remove' ? '-' : ' ' }}</span>
                                    <span class="diff-line-text">{{ line.text }}</span>
                                </div>
                            </div>
                            <div v-if="item.choicesChanged" style="margin-top: 4px">
                                <el-text type="info" size="small">选项也有变更</el-text>
                            </div>
                        </div>
                    </template>
                </div>
            </div>
        </template>
    </div>
</template>

<style scoped>
.diff-viewer {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.no-changes {
    text-align: center;
    padding: 24px;
}

.diff-section {
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    overflow: hidden;
}

.diff-section-title {
    padding: 8px 12px;
    background: var(--el-fill-color-lighter);
    border-bottom: 1px solid var(--el-border-color-lighter);
}

.diff-content {
    font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
    font-size: 13px;
    line-height: 1.6;
}

.diff-line {
    display: flex;
    min-height: 22px;
    line-height: 22px;
    padding: 0 8px;
}

.diff-line.diff-add {
    background: rgba(46, 160, 67, 0.15);
}

.diff-line.diff-remove {
    background: rgba(248, 81, 73, 0.15);
}

.diff-line-prefix {
    width: 16px;
    flex-shrink: 0;
    color: var(--el-text-color-secondary);
    user-select: none;
    text-align: center;
}

.diff-line.diff-add .diff-line-prefix {
    color: var(--el-color-success);
}

.diff-line.diff-remove .diff-line-prefix {
    color: var(--el-color-danger);
}

.diff-line-text {
    white-space: pre-wrap;
    word-break: break-all;
}

.diff-choices {
    padding: 8px 12px;
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.diff-choice {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 8px;
    border-radius: 4px;
    font-size: 13px;
}

.diff-choice.diff-add {
    background: rgba(46, 160, 67, 0.1);
}

.diff-choice.diff-remove {
    background: rgba(248, 81, 73, 0.1);
}

.diff-choice.diff-modify {
    background: rgba(230, 162, 60, 0.1);
}

.diff-choice-tag {
    flex-shrink: 0;
}

.diff-choice-correct {
    font-size: 12px;
    padding: 1px 6px;
    border-radius: 3px;
    background: var(--el-fill-color);
    color: var(--el-color-danger);
    flex-shrink: 0;
}

.diff-choice-correct.correct {
    color: var(--el-color-success);
}

.diff-add-inline {
    background: rgba(46, 160, 67, 0.3);
    border-radius: 2px;
    padding: 0 1px;
}

.diff-remove-inline {
    background: rgba(248, 81, 73, 0.3);
    border-radius: 2px;
    padding: 0 1px;
    text-decoration: line-through;
}

.diff-correct-change {
    display: flex;
    gap: 2px;
    flex-shrink: 0;
}

.diff-word-content {
    word-break: break-all;
}

.diff-sub-questions {
    padding: 8px 12px;
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.diff-sub-question {
    padding: 8px;
    border-radius: 4px;
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.diff-sub-question.diff-add {
    background: rgba(46, 160, 67, 0.1);
}

.diff-sub-question.diff-remove {
    background: rgba(248, 81, 73, 0.1);
}

.diff-sub-question.diff-modify {
    background: rgba(230, 162, 60, 0.1);
}

.diff-sub-content {
    font-size: 13px;
    white-space: pre-wrap;
    word-break: break-all;
}
</style>
