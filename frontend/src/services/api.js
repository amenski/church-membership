import axios from 'axios';

const api = axios.create({
    baseURL: '/api'
});

export default {
    // Members
    getMembers() {
        return api.get('/members');
    },
    getMemberById(id) {
        return api.get(`/members/${id}`);
    },
    createMember(member) {
        return api.post('/members', member);
    },
    updateMember(id, member) {
        return api.put(`/members/${id}`, member);
    },
    deleteMember(id) {
        return api.delete(`/members/${id}`);
    },
    getOverdueMembers(months) {
        return api.get(`/members/overdue/${months}`);
    },

    // Payments
    getPayments() {
        return api.get('/payments');
    },
    getMemberPayments(memberId) {
        return api.get(`/payments/member/${memberId}`);
    },
    createPayment(payment) {
        return api.post('/payments', payment);
    },

    // Communications
    getCommunications() {
        return api.get('/communications');
    },
    createCommunication(communication) {
        return api.post('/communications', communication);
    },
    sendToAllMembers(communication) {
        return api.post('/communications/send-to-all', communication);
    },
    sendToOverdueMembers(months, communication) {
        return api.post(`/communications/send-to-overdue/${months}`, communication);
    }
};
