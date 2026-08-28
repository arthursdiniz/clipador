package com.clipador.job.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobStateMachineTest {

    @Test
    void permitsHappyPathAndFailureTransitions() {
        assertThat(JobStateMachine.canTransition(JobStatus.RECEIVED, JobStatus.DOWNLOADING)).isTrue();
        assertThat(JobStateMachine.canTransition(JobStatus.TRANSCRIBING, JobStatus.FAILED)).isTrue();
        assertThat(JobStateMachine.canTransition(JobStatus.FAILED, JobStatus.RECEIVED)).isTrue();
    }

    @Test
    void rejectsSkippedAndTerminalTransitions() {
        assertThat(JobStateMachine.canTransition(JobStatus.RECEIVED, JobStatus.TRANSCRIBING)).isFalse();
        assertThat(JobStateMachine.canTransition(JobStatus.COMPLETED, JobStatus.RECEIVED)).isFalse();
        assertThat(JobStateMachine.canTransition(JobStatus.CANCELLED, JobStatus.RECEIVED)).isFalse();
    }
}

