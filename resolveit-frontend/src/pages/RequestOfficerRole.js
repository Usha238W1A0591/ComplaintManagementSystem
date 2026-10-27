import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import { useUIState } from '../hooks/useUIState';
import UIStateMessage from '../components/UIStateMessage';

function RequestOfficerRole() {
    const [reason, setReason] = useState('');
    const [myRequests, setMyRequests] = useState([]);
    const navigate = useNavigate();
    const uiState = useUIState();

    useEffect(() => {
        fetchMyRequests();
    }, []);

    const fetchMyRequests = async () => {
        try {
            const response = await api.get('/api/officer-requests/my-requests');
            
            // Backend returns { status, requests } - extract the requests array
            const requestsData = response.data.requests || response.data;
            
            if (Array.isArray(requestsData)) {
                setMyRequests(requestsData);
            } else {
                console.error('Expected array but got:', response.data);
                setMyRequests([]);
            }
        } catch (err) {
            console.error('Error fetching requests:', err);
            setMyRequests([]);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (!reason.trim()) {
            uiState.setError('Please provide a reason for your request');
            return;
        }

        uiState.setLoading();

        try {
            await api.post('/api/officer-requests', { reason });

            uiState.setSuccess('Officer role request submitted successfully! You will be notified via email once reviewed.');
            setReason('');
            fetchMyRequests();
        } catch (err) {
            uiState.setError(err.response?.data?.message || 'Failed to submit request. Please try again.');
        }
    };

    const handleRetry = () => {
        uiState.reset();
    };

    const getStatusBadge = (status) => {
        const styles = {
            PENDING: { bg: '#fff3cd', color: '#856404', text: '⏳ Pending' },
            APPROVED: { bg: '#d4edda', color: '#155724', text: '✅ Approved' },
            REJECTED: { bg: '#f8d7da', color: '#721c24', text: '❌ Rejected' }
        };
        const style = styles[status] || styles.PENDING;
        return (
            <span style={{
                background: style.bg,
                color: style.color,
                padding: '5px 15px',
                borderRadius: '20px',
                fontSize: '0.9rem',
                fontWeight: '600'
            }}>
                {style.text}
            </span>
        );
    };

    return (
        <div style={{ 
            background: 'linear-gradient(135deg, #e0f2fe 0%, #b3e5fc 100%)',
            minHeight: '100vh',
            width: '100%'
        }}>
            <div style={{
                padding: '40px', 
                maxWidth: '900px', 
                margin: '0 auto'
            }}>
            <button
                onClick={() => navigate('/dashboard')}
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
                    👮 Request Officer Role
                </h1>
                <p style={{ color: '#666', marginBottom: '30px' }}>
                    Submit a request to become an officer and help manage complaints in your community.
                </p>

                <UIStateMessage 
                    state={uiState.state} 
                    message={uiState.message} 
                    onRetry={handleRetry}
                />

                <form onSubmit={handleSubmit}>
                    <div style={{ marginBottom: '20px' }}>
                        <label style={{
                            display: 'block',
                            marginBottom: '8px',
                            fontWeight: '600',
                            color: '#333'
                        }}>
                            Why do you want to become an officer? *
                        </label>
                        <textarea
                            value={reason}
                            onChange={(e) => setReason(e.target.value)}
                            placeholder="Explain your motivation, experience, and how you can contribute..."
                            rows="6"
                            style={{
                                width: '100%',
                                padding: '15px',
                                borderRadius: '8px',
                                border: '2px solid #e0e0e0',
                                fontSize: '1rem',
                                fontFamily: 'inherit',
                                resize: 'vertical'
                            }}
                            required
                            disabled={uiState.isLoading || uiState.isSuccess}
                        />
                    </div>

                    <button
                        type="submit"
                        disabled={uiState.isLoading || uiState.isSuccess}
                        style={{
                            background: (uiState.isLoading || uiState.isSuccess) ? '#ccc' : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                            color: 'white',
                            border: 'none',
                            padding: '15px 40px',
                            borderRadius: '8px',
                            fontSize: '1.1rem',
                            fontWeight: '600',
                            cursor: (uiState.isLoading || uiState.isSuccess) ? 'not-allowed' : 'pointer',
                            width: '100%'
                        }}
                    >
                        {uiState.isLoading ? 'Submitting...' : uiState.isSuccess ? '✅ Request Submitted!' : '📤 Submit Request'}
                    </button>
                </form>

                {/* My Requests Section */}
                {myRequests.length > 0 && (
                    <div style={{ marginTop: '40px' }}>
                        <h2 style={{ color: '#333', marginBottom: '20px' }}>📋 My Requests</h2>
                        {myRequests.map((request) => (
                            <div
                                key={request.id}
                                style={{
                                    background: '#f8f9fa',
                                    padding: '20px',
                                    borderRadius: '12px',
                                    marginBottom: '15px',
                                    border: '1px solid #e0e0e0'
                                }}
                            >
                                <div style={{
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    alignItems: 'center',
                                    marginBottom: '10px'
                                }}>
                                    <span style={{ color: '#666', fontSize: '0.9rem' }}>
                                        Requested: {new Date(request.requestedAt).toLocaleDateString()}
                                    </span>
                                    {getStatusBadge(request.status)}
                                </div>
                                <p style={{ color: '#333', marginBottom: '10px' }}>
                                    <strong>Reason:</strong> {request.reason}
                                </p>
                                {request.reviewComment && (
                                    <div style={{
                                        background: 'white',
                                        padding: '15px',
                                        borderRadius: '8px',
                                        marginTop: '10px'
                                    }}>
                                        <strong style={{ color: '#667eea' }}>Admin Response:</strong>
                                        <p style={{ color: '#666', marginTop: '5px' }}>{request.reviewComment}</p>
                                        {request.reviewedAt && (
                                            <small style={{ color: '#999' }}>
                                                Reviewed: {new Date(request.reviewedAt).toLocaleDateString()}
                                            </small>
                                        )}
                                    </div>
                                )}
                            </div>
                        ))}
                    </div>
                )}
            </div>
            </div>
        </div>
    );
}

export default RequestOfficerRole;
