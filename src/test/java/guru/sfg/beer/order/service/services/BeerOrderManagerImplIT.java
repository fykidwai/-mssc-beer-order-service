package guru.sfg.beer.order.service.services;

import static com.github.jenspiegsa.wiremockextension.ManagedWireMockServer.with;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
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
import org.springframework.web.util.DefaultUriBuilderFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jenspiegsa.wiremockextension.Managed;
import com.github.jenspiegsa.wiremockextension.WireMockExtension;
import com.github.tomakehurst.wiremock.WireMockServer;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderLine;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.domain.Customer;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.repositories.CustomerRepository;
import guru.sfg.beer.order.service.services.beer.BeerServiceRestTemplateImpl;
import guru.sfg.brewery.model.BeerDto;

@ExtendWith(WireMockExtension.class)
@SpringBootTest
public class BeerOrderManagerImplIT {

    @Autowired
    private BeerOrderManager beerOrderManager;
    @Autowired
    private BeerOrderRepository beerOrderRepository;
    @Autowired
    private CustomerRepository customerRepository;

    /*
     * Unable to use default port configured by WireMockExtension so can't autowire and use the been created in Test
     * Configuration in RestTemplBuilderProvider
     */
//    @Autowired
    @Managed
    WireMockServer wireMockServer = with(wireMockConfig().port(9093));
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
    void testNewToAllocate() throws JsonProcessingException, InterruptedException {
//        Thread.sleep(5000);
        final BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();
        wireMockServer.stubFor(get(parseURL(BeerServiceRestTemplateImpl.BEER_UPC_PATH, "12345"))
            .willReturn(okJson(objectMapper.writeValueAsString(beerDto))));
//        Thread.sleep(5000);
        final BeerOrder beerOrder = createBeerOrder();
        final BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);
        assertNotNull(savedBeerOrder);

        await().untilAsserted(() -> {
            final BeerOrder foundOrder = beerOrderRepository.findById(beerOrder.getId()).get();

            // TODO - ALLOCATED STATUS
            assertEquals(BeerOrderStatusEnum.ALLOCATED, foundOrder.getOrderStatus());
        });
        final BeerOrder savedBeerOrder2 = beerOrderRepository.findById(savedBeerOrder.getId()).get();

        assertEquals(BeerOrderStatusEnum.ALLOCATED, savedBeerOrder2.getOrderStatus());

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
