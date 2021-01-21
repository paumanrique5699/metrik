package fourkeymetrics.pipeline

import fourkeymetrics.dto.BuildSummaryCollectionDTO
import fourkeymetrics.dto.BuildSummaryDTO
import fourkeymetrics.dto.BuildDetailsDTO
import fourkeymetrics.exception.ApplicationException
import fourkeymetrics.model.Build
import fourkeymetrics.model.Commit
import fourkeymetrics.model.Stage
import fourkeymetrics.repository.BuildRepository
import fourkeymetrics.repository.DashboardRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange
import java.nio.charset.Charset
import java.util.Base64
import kotlin.streams.toList

@Service
class Jenkins(@Autowired private var restTemplate: RestTemplate,
              @Autowired private var dashboardRepository: DashboardRepository,
              @Autowired private var buildRepository: BuildRepository) : Pipeline() {
    override fun fetchAllBuilds(dashboardId: String, pipelineId: String): List<Build> {
        val pipelineConfiguration = dashboardRepository.getPipelineConfiguration(dashboardId, pipelineId)!!
        val username = pipelineConfiguration.username
        val token = pipelineConfiguration.token
        val baseUrl = pipelineConfiguration.url

        val buildSummaries = getBuildSummariesFromJenkins(username, token, baseUrl)

        val builds = buildSummaries.parallelStream().map { buildSummary ->
            val buildDetails = getBuildDetailsFromJenkins(username, token, baseUrl, buildSummary)

            Build(pipelineId,
                buildSummary.number,
                buildSummary.result,
                buildSummary.duration,
                buildSummary.timestamp,
                buildSummary.url,
                constructBuildStages(buildDetails),
                constructBuildCommits(buildSummary).flatten()
            )
        }.toList()

        buildRepository.save(builds)

        return builds
    }

    private fun constructBuildCommits(buildSummary: BuildSummaryDTO): List<List<Commit>> {
        return buildSummary.changeSets.map { changeSetDTO ->
            changeSetDTO.items.map { commitDTO ->
                Commit(commitDTO.commitId, commitDTO.timestamp, commitDTO.date, commitDTO.msg)
            }
        }
    }

    private fun constructBuildStages(buildDetails: BuildDetailsDTO): List<Stage> {
        return buildDetails.stages.map { stageDTO ->
            Stage(stageDTO.name, stageDTO.status, stageDTO.startTimeMillis,
                stageDTO.durationMillis, stageDTO.pauseDurationMillis)
        }
    }

    private fun getBuildDetailsFromJenkins(username: String, token: String, baseUrl: String,
                                           buildSummary: BuildSummaryDTO): BuildDetailsDTO {
        val headers = setAuthHeader(username, token)
        val entity = HttpEntity<String>(headers)
        val buildDetailResponse: ResponseEntity<BuildDetailsDTO> =
            restTemplate.exchange("http://$baseUrl/${buildSummary.number}/wfapi/describe", HttpMethod.GET, entity)
        return buildDetailResponse.body!!
    }

    private fun getBuildSummariesFromJenkins(username: String, token: String,
                                             baseUrl: String): List<BuildSummaryDTO> {
        val headers = setAuthHeader(username, token)
        val entity = HttpEntity<String>(headers)
        val allBuildsResponse: ResponseEntity<BuildSummaryCollectionDTO> =
            restTemplate.exchange("http://$baseUrl/api/json?tree=allBuilds[building,number," +
                "result,timestamp,duration,url,changeSets[items[commitId,timestamp,msg,date]]]", HttpMethod.GET, entity)
        return allBuildsResponse.body!!.allBuilds
    }


    override fun verifyPipeline(url: String, username: String, token: String) {
        val headers = setAuthHeader(username, token)
        val entity = HttpEntity<String>("", headers)
        try {
            val response = restTemplate.exchange<String>(
                "$url/wfapi/", HttpMethod.GET, entity
            )
            if (!response.statusCode.is2xxSuccessful) {
                throw ApplicationException(response.statusCode, response.body!!)
            }
        } catch (e: HttpClientErrorException) {
            throw ApplicationException(e.statusCode, e.message!!)
        }

    }

    private fun setAuthHeader(username: String, token: String): HttpHeaders {
        val headers = HttpHeaders()
        val auth = "$username:$token"
        val encodedAuth = Base64.getEncoder().encodeToString(auth.toByteArray(Charset.forName("UTF-8")))
        val authHeader = "Basic $encodedAuth"
        headers.set("Authorization", authHeader)
        return headers
    }

}
