package io.quarkiverse.tekton.deployment.devui;

import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkiverse.tekton.deployment.devservices.TektonDevServiceInfoBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.ExternalPageBuilder;
import io.quarkus.devui.spi.page.Page;

public class TektonDevUIProcessor {

    private static final Logger LOG = Logger.getLogger(TektonDevUIProcessor.class);

    @BuildStep(onlyIf = IsDevelopment.class)
    void createCard(Optional<TektonDevServiceInfoBuildItem> info, BuildProducer<CardPageBuildItem> cardPage) {

        CardPageBuildItem card = new CardPageBuildItem();
        LOG.debug("Creating card page");

        info.ifPresent(i -> {
            String url = String.format("http://%s:%s", i.host(), i.hostPort());
            LOG.debug("Creating an external link page for: Tekton UI");
            card.addPage(Page.externalPageBuilder("Tekton Dashboard")
                    .doNotEmbed()
                    .icon("font-awesome-solid:code-branch")
                    .url(url));
        });

        LOG.debug("Creating an external link page for: Tekton project & version");
        final ExternalPageBuilder versionPage = Page.externalPageBuilder("Tekton project")
                .icon("font-awesome-solid:tag")
                .url("https://tekton.dev/")
                .doNotEmbed()
                .staticLabel("0.68.0");

        LOG.debug("Add version page");
        card.addPage(versionPage);
        LOG.debug("Set custom car with js");
        card.setCustomCard("qwc-tekton-card.js");
        LOG.debug("Produce ...");
        cardPage.produce(card);
    }
}
