package guru.sfg.beer.order.service.services;

import static com.github.jenspiegsa.wiremockextension.ManagedWireMockServer.with;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jenspiegsa.wiremockextension.Managed;
import com.github.jenspiegsa.wiremockextension.WireMockExtension;
import com.github.tomakehurst.wiremock.WireMockServer;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderLine;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.domain.Customer;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.repositories.CustomerRepository;
import guru.sfg.beer.order.service.services.beer.BeerServiceRestTemplateImpl;
import guru.sfg.brewery.model.BeerDto;
import guru.sfg.brewery.model.events.AllocationFailureEvent;

@ExtendWith(WireMockExtension.class)
@SpringBootTest
class BeerOrderManagerImplIT {

    @Autowired
    private BeerOrderManager beerOrderManager;
    @Autowired
    private BeerOrderRepository beerOrderRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private JmsTemplate jmsTemplate;

    /*
     * Unable to use default port configured by WireMockExtension so can't autowire and use the been created in Test
     * Configuration in RestTemplBuilderProvider
     */
//    @Autowired
    @Managed
    private final WireMockServer wireMockServer = with(wireMockConfig().port(9093));
    @Autowired
    private ObjectMapper objectMapper;

    private Customer testCustomer;
    private UUID beerId;

    @TestConfiguration
    static class RestTemplBuilderProvider {

        /*
         * @Bean(destroyMethod = "stop") public WireMockServer wireMockServer() { final WireMockServer server =
         * with(wireMockConfig().port(9093)); server.start(); return server; }
         */
    }

    @BeforeEach
    void setup() {
        beerId = getID();
        testCustomer = customerRepository.save(Customer.builder().id(getID()).customerName("TestCust").build());
    }

    @Test
    void testNewToAllocate() throws JsonProcessingException {
        final BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();
        wireMockServer.stubFor(get(parseURL(BeerServiceRestTemplateImpl.BEER_UPC_PATH, "12345"))
            .willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

        final BeerOrder beerOrder = createBeerOrder();

        final BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

        assertNotNull(savedBeerOrder);

        await().untilAsserted(() -> {
            final BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();
            assertEquals(BeerOrderStatusEnum.ALLOCATED, foundOrder.getOrderStatus());
        });

        await().untilAsserted(() -> {
            final BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();
            final BeerOrderLine line = foundOrder.getBeerOrderLines().iterator().next();
            assertEquals(line.getOrderQuantity(), line.getQuantityAllocated());
        });

        final BeerOrder savedBeerOrder2 = beerOrderRepository.findById(savedBeerOrder.getId()).get();
        assertNotNull(savedBeerOrder2);
        assertEquals(BeerOrderStatusEnum.ALLOCATED, savedBeerOrder2.getOrderStatus());
        savedBeerOrder2.getBeerOrderLines().forEach(line -> {
            assertEquals(line.getOrderQuantity(), line.getQuantityAllocated());
        });
    }

    @Test
    void testNewToPickedUp() throws JsonProcessingException {
        final BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();
        wireMockServer.stubFor(get(parseURL(BeerServiceRestTemplateImpl.BEER_UPC_PATH, "12345"))
            .willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

        final BeerOrder beerOrder = createBeerOrder();

        final BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

        assertNotNull(savedBeerOrder);

        await().untilAsserted(() -> {
            final BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();
            assertEquals(BeerOrderStatusEnum.ALLOCATED, foundOrder.getOrderStatus());
        });

        beerOrderManager.beerOrderPickedUp(savedBeerOrder.getId());

        await().untilAsserted(() -> {
            final BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();
            assertEquals(BeerOrderStatusEnum.PICKED_UP, foundOrder.getOrderStatus());
        });

        final BeerOrder pickedUpOrder = beerOrderRepository.findById(savedBeerOrder.getId()).get();

        assertEquals(BeerOrderStatusEnum.PICKED_UP, pickedUpOrder.getOrderStatus());
    }

    @Test
    void testFailedValidation() throws JsonProcessingException {
        final BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();
        wireMockServer.stubFor(get(parseURL(BeerServiceRestTemplateImpl.BEER_UPC_PATH, "12345"))
            .willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

        final BeerOrder beerOrder = createBeerOrder();
        beerOrder.setCustomerRef("fail-validation");
        final BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

        assertNotNull(savedBeerOrder);

        await().untilAsserted(() -> {
            final BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();
            assertEquals(BeerOrderStatusEnum.VALIDATION_EXCEPTION, foundOrder.getOrderStatus());
        });
    }

    @Test
    void testAllocationFailure() throws JsonProcessingException {
        final BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();
        wireMockServer.stubFor(get(parseURL(BeerServiceRestTemplateImpl.BEER_UPC_PATH, "12345"))
            .willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

        final BeerOrder beerOrder = createBeerOrder();
        beerOrder.setCustomerRef("fail-allocation");
        final BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

        assertNotNull(savedBeerOrder);

        await().untilAsserted(() -> {
            final BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();
            assertEquals(BeerOrderStatusEnum.ALLOCATION_EXCEPTION, foundOrder.getOrderStatus());
        });

        final AllocationFailureEvent allocationFailureEvent =
            (AllocationFailureEvent)jmsTemplate.receiveAndConvert(JmsConfig.ALLOCATION_FAILURE_QUEUE);
        assertThat(allocationFailureEvent).isNotNull();
        assertThat(allocationFailureEvent.getOrderId()).isEqualTo(savedBeerOrder.getId());
    }

    @Test
    void testPartialAllocation() throws JsonProcessingException {
        final BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();
        wireMockServer.stubFor(get(parseURL(BeerServiceRestTemplateImpl.BEER_UPC_PATH, "12345"))
            .willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

        final BeerOrder beerOrder = createBeerOrder();
        beerOrder.setCustomerRef("partial-allocation");

        final BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

        assertNotNull(savedBeerOrder);

        await().untilAsserted(() -> {
            final BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();
            assertEquals(BeerOrderStatusEnum.PENDING_INVENTORY, foundOrder.getOrderStatus());
        });
    }

    private String parseURL(final String uriTemplate, final Object... uriVariables) {
        return new DefaultUriBuilderFactory().uriString(uriTemplate).build(uriVariables).toString();
    }

    private BeerOrder createBeerOrder() {
        final BeerOrder beerOrder = BeerOrder.builder().customer(testCustomer).build();
        final Set<BeerOrderLine> lines = new HashSet<>();
        lines.add(BeerOrderLine.builder().beerId(beerId).upc("12345").orderQuantity(1).beerOrder(beerOrder).build());

        beerOrder.setBeerOrderLines(lines);

        return beerOrder;
    }

    private UUID getID() {
        return UUID.randomUUID();
    }
}
