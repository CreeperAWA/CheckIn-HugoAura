package indi.etern.checkIn.action.blacklist;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.NullInput;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.entities.blacklist.Blacklist;
import indi.etern.checkIn.service.dao.BlacklistService;

import java.util.List;

@Action("getBlacklist")
public class GetBlacklistAction extends BaseAction<NullInput, GetBlacklistAction.SuccessOutput> {
    private final BlacklistService blacklistService;
    
    public GetBlacklistAction(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }
    
    @Override
    public void execute(ExecuteContext<NullInput, SuccessOutput> context) {
        context.requirePermission("blacklist.view");
        final List<Blacklist> blacklist = blacklistService.findAll();
        context.resolve(new SuccessOutput(blacklist));
    }
    
    public record SuccessOutput(List<Blacklist> blacklist) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
}
