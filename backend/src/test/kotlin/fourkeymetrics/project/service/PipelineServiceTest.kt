package fourkeymetrics.project.service

import fourkeymetrics.common.model.Build
import fourkeymetrics.common.model.Stage
import fourkeymetrics.project.repository.BuildRepository
import fourkeymetrics.project.service.impl.NoopPipelineService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
internal class PipelineServiceTest {
    @Mock
    private lateinit var buildRepository: BuildRepository

    @InjectMocks
    private lateinit var pipelineService: NoopPipelineService

    private val pipelineId = "pipelineId"

    @Test
    internal fun `should return sorted stage name lists when getStagesSortedByName() called`() {
        val builds = listOf(
            Build(
                stages = listOf(
                    Stage(name = "clone"), Stage(name = "build"),
                    Stage(name = "zzz"), Stage(name = "amazing")
                )
            ),
            Build(
                stages = listOf(
                    Stage(name = "build"), Stage("good")
                )
            )
        )
        `when`(buildRepository.getAllBuilds(pipelineId)).thenReturn(builds)

        val result = pipelineService.getStagesSortedByName(pipelineId)

        assertEquals(5, result.size)
        assertEquals("amazing", result[0])
        assertEquals("build", result[1])
        assertEquals("clone", result[2])
        assertEquals("good", result[3])
        assertEquals("zzz", result[4])
    }
}