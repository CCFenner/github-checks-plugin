package io.jenkins.plugins.checks.github;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.github_branch_source.Connector;
import org.jenkinsci.plugins.github_branch_source.GitHubAppCredentials;
import org.kohsuke.github.GHCheckRunBuilder;
import org.kohsuke.github.GitHub;

import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksPublisher;

import hudson.model.TaskListener;

/**
 * A publisher which publishes GitHub check runs.
 */
public class GitHubChecksPublisher extends ChecksPublisher {
    private static final String GITHUB_URL = "https://api.github.com";
    private static final Logger LOGGER = Logger.getLogger(GitHubChecksPublisher.class.getName());

    private final GitHubChecksContext context;
    @Nullable
    private final TaskListener listener;
    private final String gitHubUrl;

    /**
     * {@inheritDoc}.
     *
     * @param context
     *         a context which contains SCM properties
     */
    public GitHubChecksPublisher(final GitHubChecksContext context, @Nullable final TaskListener listener) {
        this(context, listener, GITHUB_URL);
    }

    GitHubChecksPublisher(final GitHubChecksContext context, @Nullable final TaskListener listener,
                          final String gitHubUrl) {
        super();

        this.context = context;
        this.listener = listener;
        this.gitHubUrl = gitHubUrl;
    }

    /**
     * Publishes a GitHub check run.
     *
     * @param details
     *         the details of a check run
     */
    @Override
    public void publish(final ChecksDetails details) {
        try {
            GitHubAppCredentials credentials = context.getCredentials();
            GitHub gitHub = Connector.connect(StringUtils.defaultIfBlank(credentials.getApiUri(), gitHubUrl),
                    credentials);

            GitHubChecksDetails gitHubDetails = new GitHubChecksDetails(details);
            createBuilder(gitHub, gitHubDetails).create();
            if (listener != null) {
                listener.getLogger().printf("GitHub check (name: %s, status: %s) has been published.%n",
                        gitHubDetails.getName(), gitHubDetails.getStatus());
            }
        }
        catch (IllegalStateException | IOException e) {
            String message = "Failed Publishing GitHub checks: ";
            LOGGER.log(Level.WARNING, (message + details).replaceAll("[\r\n]", ""), e);
            if (listener != null) {
                listener.getLogger().println(message + e);
            }
        }
    }

    @VisibleForTesting
    GHCheckRunBuilder createBuilder(final GitHub gitHub, final GitHubChecksDetails details) throws IOException {
        GHCheckRunBuilder builder = gitHub.getRepository(context.getRepository())
                .createCheckRun(details.getName(), context.getHeadSha())
                .withStatus(details.getStatus())
                .withDetailsURL(details.getDetailsURL().orElse(context.getURL()))
                .withStartedAt(details.getStartedAt().orElse(Date.from(Instant.now())));

        if (details.getConclusion().isPresent()) {
            builder.withConclusion(details.getConclusion().get())
                    .withCompletedAt(details.getCompletedAt().orElse(Date.from(Instant.now())));
        }

        details.getOutput().ifPresent(builder::add);
        details.getActions().forEach(builder::add);

        return builder;
    }
}
