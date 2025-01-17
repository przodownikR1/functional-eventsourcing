package io.pillopl.eventsource.shop.domain

import io.pillopl.eventsource.shop.domain.commands.MarkPaymentTimeout
import io.pillopl.eventsource.shop.domain.commands.OrderWithTimeout
import io.pillopl.eventsource.shop.domain.commands.Pay
import io.pillopl.eventsource.shop.domain.events.ItemPaid
import io.pillopl.eventsource.shop.domain.events.ItemOrdered
import io.pillopl.eventsource.shop.domain.events.ItemPaymentTimeout
import io.vavr.control.Try
import spock.lang.Specification
import spock.lang.Unroll

import static io.pillopl.eventsource.shop.ShopItemFixture.ordered
import static io.pillopl.eventsource.shop.ShopItemFixture.initialized
import static io.pillopl.eventsource.shop.ShopItemFixture.paid
import static io.pillopl.eventsource.shop.ShopItemFixture.withTimeout
import static io.pillopl.eventsource.shop.ShopItemFixture.withTimeoutAndPaid
import static java.time.Instant.now
import static java.time.Instant.parse

@Unroll
class ShopItemSpec extends Specification {

    private static final int PAYMENT_DEADLINE_IN_HOURS = 48
    private static final BigDecimal ANY_PRICE = BigDecimal.TEN
    private final UUID uuid = UUID.randomUUID()

    def 'should emit item ordered event when ordering initialized item'() {
        given:
            ShopItem initialized = initialized()
        when:
            Try<ShopItem> tryOrder = initialized.order(new OrderWithTimeout(uuid, ANY_PRICE, now(), PAYMENT_DEADLINE_IN_HOURS));
        then:
            tryOrder.isSuccess()
            tryOrder.get().getUncommittedChanges().size() == 1
            tryOrder.get().getUncommittedChanges().head().type() == ItemOrdered.TYPE
    }

    def 'should calculate #deadline when ordering at #orderingAt and expiration in days #expiresIn'() {
        given:
            ShopItem initialized = initialized()
        when:
        Try<ShopItem> tryOrder = initialized.order(new OrderWithTimeout(uuid, ANY_PRICE, parse(orderingAt), expiresInMinutes))
        then:
            tryOrder.isSuccess()
            ((ItemOrdered) tryOrder.get().getUncommittedChanges().head()).paymentTimeoutDate == parse(deadline)
        where:
            orderingAt             | expiresInMinutes || deadline
            "1995-10-23T10:12:35Z" | 0                || "1995-10-23T10:12:35Z"
            "1995-10-23T10:12:35Z" | 1                || "1995-10-23T10:13:35Z"
            "1995-10-23T10:12:35Z" | 2                || "1995-10-23T10:14:35Z"
            "1995-10-23T10:12:35Z" | 20               || "1995-10-23T10:32:35Z"
            "1995-10-23T10:12:35Z" | 24               || "1995-10-23T10:36:35Z"
    }

    def 'Payment expiration date cannot be in the past'() {
        given:
            ShopItem initialized = initialized()
        when:
            Try<ShopItem> tryOrder = initialized.order(new OrderWithTimeout(uuid, ANY_PRICE, now(), -1))
        then:
            tryOrder.isFailure()
            tryOrder.getCause().message.contains("Payment timeout day is before ordering date")
    }

    def 'ordering an item should be idempotent'() {
        given:
            ShopItem ordered = ordered(uuid)
        when:
            Try<ShopItem> tryOrder = ordered.order(new OrderWithTimeout(uuid, ANY_PRICE, now(), PAYMENT_DEADLINE_IN_HOURS))
        then:
            tryOrder.isSuccess()
            tryOrder.get().getUncommittedChanges().isEmpty()
    }

    def 'cannot pay for just initialized item'() {
        given:
            ShopItem initialized = initialized()
        when:
            Try<ShopItem> tryPay = initialized.pay(new Pay(uuid, now()))
        then:
            tryPay.isFailure()
    }

    def 'cannot mark payment timeout when item just initialized'() {
        given:
            ShopItem item = initialized()
        when:
            Try<ShopItem> tryMark = item.markTimeout(new MarkPaymentTimeout(uuid, now()))
        then:
            tryMark.isFailure()
    }

    def 'should emit item paid event when paying for ordered item'() {
        given:
            ShopItem ordered = ordered(uuid)
        when:
            Try<ShopItem> tryPay = ordered.pay(new Pay(uuid, now()))
        then:
            tryPay.isSuccess()
            tryPay.get().getUncommittedChanges().size() == 1
            tryPay.get().getUncommittedChanges().head().type() == ItemPaid.TYPE
    }

    def 'paying for an item should be idempotent'() {
        given:
            ShopItem paid = paid(uuid)
        when:
            Try<ShopItem> tryPay = paid.pay(new Pay(uuid, now()))
        then:
            tryPay.isSuccess()
            tryPay.get().getUncommittedChanges().isEmpty()
    }

    def 'should emit payment timeout event when marking item as payment missing'() {
        given:
            ShopItem ordered = ordered(uuid)
        when:
            Try<ShopItem> tryMark = ordered.markTimeout(new MarkPaymentTimeout(uuid, now()))
        then:
            tryMark.isSuccess()
            tryMark.get().getUncommittedChanges().size() == 1
            tryMark.get().getUncommittedChanges().head().type() == ItemPaymentTimeout.TYPE
    }

    def 'marking payment timeout should be idempotent'() {
        when:
            Try<ShopItem> tryMark = withTimeout(uuid).markTimeout(new MarkPaymentTimeout(uuid, now()))
        then:
            tryMark.isSuccess()
            tryMark.get().getUncommittedChanges().isEmpty()
    }

    def 'cannot mark payment missing when item already paid'() {
        given:
            ShopItem paid = paid(uuid)
        when:
            Try<ShopItem> tryMarkTimeout = paid.markTimeout(new MarkPaymentTimeout(uuid, now()))
        then:
            tryMarkTimeout.isFailure()
    }

    def 'should emit item paid event when receiving missed payment'() {
        when:
            Try<ShopItem> tryPay = withTimeout(uuid).pay(new Pay(uuid, now()))
        then:
            tryPay.isSuccess()
            tryPay.get().getUncommittedChanges().size() == 1
            tryPay.get().getUncommittedChanges().head().type() == ItemPaid.TYPE

    }

    def 'receiving payment after timeout should be idempotent'() {
        given:
            ShopItem paidAfterTImeout = withTimeoutAndPaid(uuid)
        when:
            Try<ShopItem> tryPay = paidAfterTImeout.pay(new Pay(uuid, now()))
        then:
            tryPay.isSuccess()
            tryPay.get().getUncommittedChanges().isEmpty()
    }

}
