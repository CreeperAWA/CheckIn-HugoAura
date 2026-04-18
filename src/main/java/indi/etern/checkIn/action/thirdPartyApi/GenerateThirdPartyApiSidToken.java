package indi.etern.checkIn.action.thirdPartyApi;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.auth.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;

@Action("generateThirdPartyApiSidToken")
public class GenerateThirdPartyApiSidToken extends BaseAction<GenerateThirdPartyApiSidToken.Input, GenerateThirdPartyApiSidToken.SuccessOutput> {
    public record Input(String sid) implements InputData {}

    public record SuccessOutput(LinkedHashMap<String, Object> data) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }

    private static JwtTokenProvider jwtTokenProvider;

    @Autowired
    public void setJwtTokenProvider(JwtTokenProvider jwtTokenProvider) {
        GenerateThirdPartyApiSidToken.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void execute(ExecuteContext<Input, SuccessOutput> context) {
        context.requirePermission("thirdPartyApi.manage.setting");
        String sid = context.getInput().sid;
        if (sid == null || sid.trim().isEmpty()) {
            context.resolve(new SuccessOutput(new LinkedHashMap<String, Object>() {{
                put("error", "SID 不能为空");
            }}));
            return;
        }
        try {
            String token = jwtTokenProvider.generateThirdPartyApiSidToken(sid.trim());
            context.resolve(new SuccessOutput(new LinkedHashMap<String, Object>() {{
                put("token", token);
            }}));
        } catch (Exception e) {
            context.resolve(new SuccessOutput(new LinkedHashMap<String, Object>() {{
                put("error", "Token 生成失败: " + e.getMessage());
            }}));
        }
    }
}
