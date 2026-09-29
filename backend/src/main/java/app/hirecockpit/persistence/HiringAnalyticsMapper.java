package app.hirecockpit.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface HiringAnalyticsMapper extends BaseMapper<CompanyEntity> {
    @Select("SELECT current_stage AS stage,COUNT(*) AS count FROM application WHERE workspace_id=UUID_TO_BIN(#{workspaceId}) AND deleted_at IS NULL GROUP BY current_stage ORDER BY current_stage")
    List<Map<String,Object>> funnel(@Param("workspaceId") String workspaceId);
}
