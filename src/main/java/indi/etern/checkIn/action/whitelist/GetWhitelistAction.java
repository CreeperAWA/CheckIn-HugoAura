package indi.etern.checkIn.action.whitelist;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.NullInput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.entities.whitelist.Whitelist;
import indi.etern.checkIn.service.dao.WhitelistService;

import java.util.List;

@Action("getWhitelist")
public class GetWhitelistAction extends BaseAction<NullInput, GetWhitelistAction.SuccessOutput> {
    private final WhitelistService whitelistService;
    
    public GetWhitelistAction(WhitelistService whitelistService) {
        this.whitelistService = whitelistService;
    }
    
    @Override
    public void execute(ExecuteContext<NullInput, SuccessOutput> context) {
        context.requirePermission("robotApi.view.whitelist");
        final List<Whitelist> whitelist = whitelistService.findAll();
        context.resolve(new SuccessOutput(whitelist));
    }
    
    public record SuccessOutput(List<Whitelist> whitelist) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
}
