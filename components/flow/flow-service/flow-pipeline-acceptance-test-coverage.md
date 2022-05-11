# Flow pipeline acceptance test coverage

The test coverage of the flow event pipeline's acceptance tests are documented here.

This document should be maintained so that we can ensure that we have quick visibility into our coverage as we are expecting a number of scenarios to be exercised.

## Sending
- Calling 'send' on initiated sessions sends a session data event and schedules a wakeup event ✅
- Calling 'send' on a closed session schedules an error event (not fully implemented, assert CLOSING, CLOSED, WAIT_FOR_FINAL_ACK states)
- Calling 'send' multiple times on initiated sessions resumes the flow and sends a session data events each time ✅
- Given a flow resumes after receiving a session data event calling 'send' on the session sends a session data event and no session ack ✅
- Given a flow resumes after receiving session data events calling 'send' on the sessions sends session data events and no session ack for the session that resumed the flow ✅

## Send + receiving

- Calling 'sendAndReceive' on an initiated session sends a session data event ✅
- Calling 'sendAndReceive' on a closed session schedules an error event
- Given a flow resumes after receiving session data events calling 'sendAndReceive' on the sessions sends session data events and no session ack for the session that resumed the flow ✅

## Receiving (can use parameterised tests to assert the same behaviour for sendAndReceive)

- Receiving an out-of-order session data events does not resume the flow and sends a session ack ✅
- Receiving a wakeup or session ack event does not resume the flow and resends any unacknowledged events ✅ (Still requires the resends to be asserted)
- Receiving all session data events for closing sessions resumes the flow and sends a session ack (not fully implemented)
- Receiving a session event for an unrelated session does not resume the flow and sends a session ack ✅
- Given two sessions receiving a single session data event does not resume the flow and sends a session ack ✅
- Given two sessions receiving all session data events resumes the flow and sends session acks ✅
- Given two sessions where one has already received a session close event calling 'receive' and then receiving a session data event for the other session resumes the flow and sends a session ack ✅
- Given two sessions have already received their session data events when the flow calls 'receive' for both sessions at once the flow should schedule a wakeup event ✅
- Given two sessions have already received their session data events when the flow calls 'receive' for each session individually the flow should schedule a wakeup event ✅
- Given two sessions receiving a session close event for one resumes the flow with an error (not fully implemented, assert WAIT_FOR_FINAL_ACK session as well)
- Given two sessions receiving a session data event for one and a close for another resumes the flow with an error
- Given a non-receive request type receiving a session data event does not resume the flow and sends a session acks ✅

## Closing

- Calling 'close' on initiated sessions sends session close events ✅
- Calling 'close' on an initiated and closed session sends a session close event to the initiated session  ✅
- Receiving an out-of-order session close events does not resume the flow and sends a session ack ✅
- Receiving a wakeup or session ack event does not resume the flow and resends any unacknowledged events ✅ (Still requires the resends to be asserted)
- Receiving a session close event for one session and a data for another resumes the flow with an error
- Receiving a session event for an unrelated session does not resume the flow and sends a session ack ✅
- Given two sessions receiving a single session close event does not resume the flow and sends a session ack ✅
- Given two sessions receiving all session close events resumes the flow and sends session acks ✅
- Given two sessions where one has already received a session close event calling 'close' and then receiving a session close event for the other session does not resume the flow and sends a session ack ✅
- Given two sessions where one enters WAIT_FOR_FINAL_ACK after calling 'close' resumes the flow after receiving a session ack and session close ✅ (test done twice but order switched around)
- Given two sessions have already received their session close events when the flow calls 'close' for both sessions at once the flow resumes after receiving session acks from each ✅
- Given two sessions have already received their session close events when the flow calls 'close' for each session individually the flow resumes after receiving session acks respectively ✅
- Given two closed sessions when the flow calls 'close' for both sessions a wakeup event is scheduled and no session close events are sent ✅
- Given a non-close request type receiving a session close event does not resume the flow and sends a session ack ✅
- Given a flow resumes after receiving session data events calling 'close' on the sessions sends session close events and no session ack for the session that resumed the flow ✅

## SubFlow session closing

- Given a subFlow contains only initiated sessions when the subFlow finishes session close events are sent ✅
- Given a subFlow contains an initiated and closed session when the subFlow finishes a single session close event is sent ✅
- Given a subFlow contains only closed sessions when the subFlow finishes a wakeup event is scheduled ✅
- Given a subFlow contains no sessions when the subFlow finishes a wakeup event is scheduled ✅
- Receiving an out-of-order session close events does not resume the flow and sends a session ack ✅
- Receiving a wakeup or session ack event does not resume the flow and resends any unacknowledged events ✅ (Still requires the resends to be asserted)
- Receiving a session close event for one session and a data for another resumes the flow with an error
- Receiving a session event for an unrelated session does not resume the flow and sends a session ack ✅
- Given two sessions receiving a single session close event does not resume the flow and sends a session ack ✅
- Given two sessions receiving all session close events resumes the flow and sends session acks ✅
- Given two sessions where one has already received a session close event calling 'close' and then receiving a session close event for the other session does not resume the flow and sends a session ack ✅
- Given two sessions where one enters WAIT_FOR_FINAL_ACK after calling 'close' resumes the flow after receiving a session ack and session close ✅ (test done twice but order switched around)