package visitor;

import models.BugTicket;
import models.FeatureRequestTicket;
import models.UiFeedbackTicket;

public interface Visitor {
    void visit(BugTicket bugTicket);
    void visit(FeatureRequestTicket featureRequestTicket);
    void visit(UiFeedbackTicket uiFeedbackTicket);
}