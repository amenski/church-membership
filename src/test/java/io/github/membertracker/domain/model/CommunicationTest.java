package io.github.membertracker.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.membertracker.domain.exception.CommunicationDomainException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CommunicationTest {

    @Test
    void newCommunicationIsNotSent() {
        assertThat(new Communication().isSent()).isFalse();
    }

    @Test
    void markAsSentSetsTheDate() {
        Communication c = new Communication();
        LocalDateTime before = LocalDateTime.now();

        c.markAsSent();

        assertThat(c.isSent()).isTrue();
        assertThat(c.getSentDate()).isBetween(before, LocalDateTime.now());
    }

    @Test
    void markAsSentTwiceThrows() {
        Communication c = new Communication();
        c.markAsSent();

        assertThatThrownBy(c::markAsSent).isInstanceOf(CommunicationDomainException.class);
    }

    @Test
    void addDeliveryInitialisesANullList() {
        Communication c = new Communication();
        c.setDeliveries(null);
        MessageDelivery delivery = new MessageDelivery();

        c.addDelivery(delivery);

        assertThat(c.getDeliveries()).containsExactly(delivery);
    }

    @Test
    void addDeliveryAppends() {
        Communication c = new Communication();
        MessageDelivery first = new MessageDelivery();
        MessageDelivery second = new MessageDelivery();

        c.addDelivery(first);
        c.addDelivery(second);

        assertThat(c.getDeliveries()).containsExactly(first, second);
    }
}
