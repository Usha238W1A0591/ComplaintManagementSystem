import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import { useUIState } from '../hooks/useUIState';
import UIStateMessage from '../components/UIStateMessage';

function ManageOfficerRequests() {
    const [requests, setRequests] = useState([]);
    const [loading, setLoading] = useState(true);
    const [reviewingId, setReviewingId] = useState(null);
    const [reviewComment, setReviewComment] = useState('');
    const [actionStates, setActionStates] = useState({}); // Track UI state per request
    const navigate = useNavigate();
    const globalUIState = useUIState(); // For general messages

    useEffect(() => {
        fetchPendingRequests();
    }, []);

    const getActionState = (requestId) => {
        return actionStates[requestId] || { state: 'idle', message: '' };
    };

    const setActionState = (requestId, state, message = '') => {
        setActionStates(prev => ({
            ...prev,
            [requestId]: { state, message }
        }));
    };

    const fetchPendingRequests = async () => {
        try {
            const response = await api.get('/api/officer-requests/pending');
            
            // Backend returns { status, requests } - extract the requests array
            const requestsData = response.data.requests || response.data;
            
            if (Array.isArray(requestsData)) {
                setRequests(requestsData);
            } else {
                console.error('Expected array but got:', response.data);
                setRequests([]);
                globalUIState.setError('Invalid response format from server');
            }
        } catch (err) {
            globalUIState.setError('Failed to load officer requests: ' + (err.response?.data?.message || err.message));
            setRequests([]);
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const handleApprove = async (requestId) => {
        if (!window.confirm('Are you sure you want to approve this officer request?')) {
            return;
        }

        setActionState(requestId, 'loading', 'Approving request...');

        try {
            await api.put(
                `/api/officer-requests/${requestId}/approve`,
                { comment: reviewComment }
            );
            
            setActionState(requestId, 'success', 'Officer request approved successfully! User has been granted officer role.');
            setReviewingId(null);
            setReviewComment('');
            
            setTimeout(() => {
                fetchPendingRequests();
                setActionState(requestId, 'idle');
            }, 2000);
        } catch (err) {
            setActionState(requestId, 'error', err.response?.data?.message || 'Failed to approve request');
        }
    };

    const handleReject = async (requestId) => {
        if (!reviewComment.trim()) {
            setActionState(requestId, 'error', 'Please provide a reason for rejection');
            return;
        }

        if (!window.confirm('Are you sure you want to reject this officer request?')) {
            return;
        }

        setActionState(requestId, 'loading', 'Rejecting request...');

        try {
            await api.put(
                `/api/officer-requests/${requestId}/reject`,
                { comment: reviewComment }
            );
            
            setActionState(requestId, 'success', 'Officer request rejected. User has been notified.');
            setReviewingId(null);
            setReviewComment('');
            
            setTimeout(() => {
                fetchPendingRequests();
                setActionState(requestId, 'idle');
            }, 2000);
        } catch (err) {
            setActionState(requestId, 'error', err.response?.data?.message || 'Failed to reject request');
        }
    };

    const handleRetryAction = (requestId) => {
        setActionState(requestId, 'idle');
    };

    if (loading) {
        return (
            <div style={{ padding: '40px', textAlign: 'center' }}>
                <h2>Loading officer requests...</h2>
            </div>
        );
    }

    return (
        <div style={{ 
            background: 'linear-gradient(135deg, #e8f5e8 0%, #c8e6c9 100%)',
            minHeight: '100vh',
            width: '100%'
        }}>
            <div style={{
                padding: '40px', 
                maxWidth: '1200px', 
                margin: '0 auto'
            }}>
            <button
                onClick={() => navigate('/officer-dashboard')}
                style={{
                    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                    color: 'white',
                    border: 'none',
                    padding: '10px 20px',
                    borderRadius: '8px',
                    cursor: 'pointer',
                    marginBottom: '20px',
                    fontWeight: '600'
                }}
            >
                ← Back to Dashboard
            </button>

            <div style={{
                background: 'white',
                borderRadius: '16px',
                padding: '40px',
                boxShadow: '0 10px 40px rgba(0,0,0,0.1)'
            }}>
                <h1 style={{
                    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                    WebkitBackgroundClip: 'text',
                    WebkitTextFillColor: 'transparent',
                    marginBottom: '10px'
                }}>
                    👮 Manage Officer Requests
                </h1>
                <p style={{ color: '#666', marginBottom: '30px' }}>
                    Review and approve/reject requests from citizens who want to become officers.
                </p>

                <UIStateMessage 
                    state={globalUIState.state} 
                    message={globalUIState.message} 
                    onRetry={() => globalUIState.reset()}
                />

                {requests.length === 0 ? (
                    <div style={{
                        textAlign: 'center',
                        padding: '60px 20px',
                        color: '#999'
                    }}>
                        <h2 style={{ fontSize: '3rem', marginBottom: '20px' }}>📭</h2>
                        <h3>No Pending Requests</h3>
                        <p>There are no officer role requests waiting for review.</p>
                    </div>
                ) : (
                    <div>
                        <div style={{
                            background: '#f0f7ff',
                            padding: '15px',
                            borderRadius: '8px',
                            marginBottom: '20px',
                            border: '1px solid #b3d9ff'
                        }}>
                            <strong>📊 {requests.length}</strong> pending request{requests.length !== 1 ? 's' : ''}
                        </div>

                        {requests.map((request) => {
                            const actionState = getActionState(request.id);
                            const isProcessing = actionState.state === 'loading' || actionState.state === 'success';
                            
                            return (
                                <div
                                    key={request.id}
                                    style={{
                                        background: '#f8f9fa',
                                        padding: '25px',
                                        borderRadius: '12px',
                                        marginBottom: '20px',
                                        border: '2px solid #e0e0e0'
                                    }}
                                >
                                    <div style={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        alignItems: 'start',
                                        marginBottom: '15px'
                                    }}>
                                        <div>
                                            <h3 style={{ color: '#333', marginBottom: '5px' }}>
                                                {request.user.fullName}
                                            </h3>
                                            <p style={{ color: '#666', fontSize: '0.9rem' }}>
                                                📧 {request.user.email}
                                            </p>
                                            <p style={{ color: '#999', fontSize: '0.85rem' }}>
                                                Requested: {new Date(request.requestedAt).toLocaleString()}
                                            </p>
                                        </div>
                                        <span style={{
                                            background: '#fff3cd',
                                            color: '#856404',
                                            padding: '5px 15px',
                                            borderRadius: '20px',
                                            fontSize: '0.9rem',
                                            fontWeight: '600'
                                        }}>
                                            ⏳ Pending
                                        </span>
                                    </div>

                                    <div style={{
                                        background: 'white',
                                        padding: '20px',
                                        borderRadius: '8px',
                                        marginBottom: '15px'
                                    }}>
                                        <strong style={{ color: '#667eea' }}>Reason for Request:</strong>
                                        <p style={{ color: '#333', marginTop: '10px', lineHeight: '1.6' }}>
                                            {request.reason}
                                        </p>
                                    </div>

                                    <UIStateMessage 
                                        state={actionState.state} 
                                        message={actionState.message} 
                                        onRetry={() => handleRetryAction(request.id)}
                                    />

                                    {reviewingId === request.id ? (
                                        <div style={{
                                            background: 'white',
                                            padding: '20px',
                                            borderRadius: '8px',
                                            marginTop: '15px'
                                        }}>
                                            <label style={{
                                                display: 'block',
                                                marginBottom: '8px',
                                                fontWeight: '600',
                                                color: '#333'
                                            }}>
                                                Add Comment (optional for approval, required for rejection):
                                            </label>
                                            <textarea
                                                value={reviewComment}
                                                onChange={(e) => setReviewComment(e.target.value)}
                                                placeholder="Add feedback or reason for your decision..."
                                                rows="3"
                                                style={{
                                                    width: '100%',
                                                    padding: '12px',
                                                    borderRadius: '8px',
                                                    border: '2px solid #e0e0e0',
                                                    fontSize: '1rem',
                                                    fontFamily: 'inherit',
                                                    marginBottom: '15px'
                                                }}
                                                disabled={isProcessing}
                                            />
                                            <div style={{ display: 'flex', gap: '10px' }}>
                                                <button
                                                    onClick={() => handleApprove(request.id)}
                                                    disabled={isProcessing}
                                                    style={{
                                                        background: isProcessing ? '#ccc' : 'linear-gradient(135deg, #11998e 0%, #38ef7d 100%)',
                                                        color: 'white',
                                                        border: 'none',
                                                        padding: '12px 24px',
                                                        borderRadius: '8px',
                                                        cursor: isProcessing ? 'not-allowed' : 'pointer',
                                                        fontWeight: '600',
                                                        flex: 1
                                                    }}
                                                >
                                                    {actionState.state === 'loading' ? 'Approving...' : actionState.state === 'success' ? '✅ Approved!' : '✅ Approve'}
                                                </button>
                                                <button
                                                    onClick={() => handleReject(request.id)}
                                                    disabled={isProcessing}
                                                    style={{
                                                        background: isProcessing ? '#ccc' : 'linear-gradient(135deg, #ee0979 0%, #ff6a00 100%)',
                                                        color: 'white',
                                                        border: 'none',
                                                        padding: '12px 24px',
                                                        borderRadius: '8px',
                                                        cursor: isProcessing ? 'not-allowed' : 'pointer',
                                                        fontWeight: '600',
                                                        flex: 1
                                                    }}
                                                >
                                                    {actionState.state === 'loading' ? 'Rejecting...' : actionState.state === 'success' ? '❌ Rejected!' : '❌ Reject'}
                                                </button>
                                                <button
                                                    onClick={() => {
                                                        setReviewingId(null);
                                                        setReviewComment('');
                                                        setActionState(request.id, 'idle');
                                                    }}
                                                    disabled={isProcessing}
                                                    style={{
                                                        background: isProcessing ? '#ccc' : '#6c757d',
                                                        color: 'white',
                                                        border: 'none',
                                                        padding: '12px 24px',
                                                        borderRadius: '8px',
                                                        cursor: isProcessing ? 'not-allowed' : 'pointer',
                                                        fontWeight: '600'
                                                    }}
                                                >
                                                    Cancel
                                                </button>
                                            </div>
                                        </div>
                                    ) : (
                                        <button
                                            onClick={() => setReviewingId(request.id)}
                                            disabled={isProcessing}
                                            style={{
                                                background: isProcessing ? '#ccc' : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                                                color: 'white',
                                                border: 'none',
                                                padding: '12px 30px',
                                                borderRadius: '8px',
                                                cursor: isProcessing ? 'not-allowed' : 'pointer',
                                                fontWeight: '600',
                                                width: '100%'
                                            }}
                                        >
                                            📝 Review Request
                                        </button>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>
            </div>
        </div>
    );
}

export default ManageOfficerRequests;
