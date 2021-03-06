package guru.sfg.beer.order.service.services.testcomponets;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.brewery.model.events.AllocateOrderRequest;
import guru.sfg.brewery.model.events.AllocateOrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Component
public class BeerOrderAllocationListener {

    private final JmsTemplate jmsTemplate;

    @JmsListener(destination = JmsConfig.ALLOCATE_ORDER_QUEUE)
    public void listen(final Message<AllocateOrderRequest> msg) {
        final AllocateOrderRequest request = msg.getPayload();

        final boolean allocationError = "fail-allocation".equals(request.getBeerOrderDto().getCustomerRef());
        final boolean pendingInventory = "partial-allocation".equals(request.getBeerOrderDto().getCustomerRef());
        final boolean sendResponse = !"dont-allocate".equals(request.getBeerOrderDto().getCustomerRef());

        request.getBeerOrderDto().getBeerOrderLines().forEach(beerOrderLineDto -> {
            beerOrderLineDto.setQuantityAllocated(
                pendingInventory ? beerOrderLineDto.getOrderQuantity() - 1 : beerOrderLineDto.getOrderQuantity());
        });
        if (sendResponse) {
            jmsTemplate.convertAndSend(JmsConfig.ALLOCATE_ORDER_RESPONSE_QUEUE,
                AllocateOrderResult.builder().beerOrderDto(request.getBeerOrderDto()).pendingInventory(pendingInventory)
                    .allocationError(allocationError).build());
        }
    }
}