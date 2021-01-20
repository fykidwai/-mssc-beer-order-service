package guru.sfg.beer.order.service.services.beer;

import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import guru.sfg.brewery.model.BeerDto;

@Service
@ConfigurationProperties(prefix = "sfg.brewery")
public class BeerServiceRestTemplateImpl implements BeerService {

    private static final String BEER_V1_PATH = "/api/v1/beer/{beerId}";
    private static final String BEER_UPC_PATH = "/api/v1/beerUpc/{upc}";
    private final RestTemplate restTemplate;

    private String beerServiceHost;

    public void setBeerServiceHost(final String beerServiceHost) {
        this.beerServiceHost = beerServiceHost;
    }

    public BeerServiceRestTemplateImpl(final RestTemplateBuilder restTemplateBuilder) {
        restTemplate = restTemplateBuilder.build();
    }

    @Override
    public Optional<BeerDto> getBeerById(final UUID beerId) {
        return Optional.of(restTemplate.getForObject(beerServiceHost + BEER_V1_PATH, BeerDto.class, beerId));
    }

    @Override
    public Optional<BeerDto> getBeerByUpc(final String upc) {
        return Optional.of(restTemplate.getForObject(beerServiceHost + BEER_UPC_PATH, BeerDto.class, upc));
    }

}
