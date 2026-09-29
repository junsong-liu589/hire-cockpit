package app.hirecockpit.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("company")
public class CompanyEntity {
    @TableId(value="id",type=IdType.INPUT)
    private String id;
    public String getId(){return id;}
    public void setId(String id){this.id=id;}
}
